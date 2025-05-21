package com.ssafy.lanterns.data.source.ble.gatt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class AudioGattServerManager(
    private val context: Context,
    private val onData: (String, ByteArray) -> Unit,
    private val onClientConnected: (String) -> Unit = {},
    private val onClientDisconnected: (String) -> Unit = {}
) {
    companion object {
        private val SERVICE_UUID = UUID.fromString("0000beef-0000-1000-8000-00805f9b34fb")
        private val CHAR_UUID    = UUID.fromString("0000cafe-0000-1000-8000-00805f9b34fb")
        private val DESC_UUID    = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val MAX_RETRY_COUNT = 3
    }

    private val handler = Handler(Looper.getMainLooper())
    private val clients = ConcurrentHashMap<String, BluetoothDevice>()
    private val subs    = ConcurrentHashMap<String, Boolean>()
    private val retries = ConcurrentHashMap<String, Int>()
    private var server: BluetoothGattServer? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var isServerRunning = false

    fun openGattServer() {
        if (server != null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        try {
            val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            server = mgr.openGattServer(context, gattCb)?.apply {
                addService(createService())
                isServerRunning = true
            }
        } catch (e: Exception) {
            isServerRunning = false
            handler.postDelayed({ openGattServer() }, 2000)
        }
    }

    private fun createService(): BluetoothGattService {
        val srv = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        characteristic = BluetoothGattCharacteristic(
            CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        ).apply {
            addDescriptor(
                BluetoothGattDescriptor(DESC_UUID, BluetoothGattDescriptor.PERMISSION_WRITE)
            )
        }
        srv.addCharacteristic(characteristic)
        return srv
    }

    fun broadcastAudioData(data: ByteArray) {
        if (!isServerRunning) {
            reopenServerIfNeeded()
            return
        }

        val currentCharacteristic = characteristic ?: return

        currentCharacteristic.value = data
        handler.post {
            val subscribedClients = clients
                .filterKeys { address -> subs[address] == true }

            if (subscribedClients.isEmpty()) {
                return@post
            }

            subscribedClients.values.forEach { dev ->
                try {
                    server?.notifyCharacteristicChanged(dev, currentCharacteristic, false)
                    retries[dev.address] = 0
                } catch (e: Exception) {
                    handleNotificationFailure(dev)
                }
            }
        }
    }

    private fun handleNotificationFailure(device: BluetoothDevice) {
        val address = device.address
        val currentRetries = retries.getOrDefault(address, 0)

        if (currentRetries < MAX_RETRY_COUNT) {
            retries[address] = currentRetries + 1
        } else {
            clients.remove(address)
            subs.remove(address)
            retries.remove(address)
            onClientDisconnected(address)
        }
    }

    private fun reopenServerIfNeeded() {
        closeServer()
        handler.postDelayed({ openGattServer() }, 1000)
    }

    fun checkServerStatus() {
        if (!isServerRunning) {
            reopenServerIfNeeded()
        }
    }

    fun isClientConnected(address: String): Boolean {
        return clients.containsKey(address) && subs[address] == true
    }

    fun getConnectedClients(): List<String> {
        return clients.keys().toList()
    }

    fun closeServer() {
        try {
            server?.close()
        } catch (e: Exception) {
            // Error closing GATT server
        } finally {
            server = null
            isServerRunning = false
            clients.clear()
            subs.clear()
            retries.clear()
        }
    }

    private val gattCb = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(
            dev: BluetoothDevice, status: Int, newState: Int
        ) {
            val address = dev.address

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                clients[address] = dev
                retries[address] = 0
                subs[address] = false
                onClientConnected(address)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                clients.remove(address)
                subs.remove(address)
                retries.remove(address)
                onClientDisconnected(address)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            val address = device.address

            if (descriptor.uuid == DESC_UUID && value != null) {
                subs[address] = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            }

            if (responseNeeded) {
                try {
                    server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                } catch (e: Exception) {
                    // Failed to send descriptor response
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            val address = device.address
            if (characteristic.uuid == CHAR_UUID && value != null) {
                onData(address, value)

                if (responseNeeded) {
                    try {
                        server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    } catch (e: Exception) {
                        // Failed to send characteristic response
                    }
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            // MTU change noted, no specific action taken by default
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handler.postDelayed({ reopenServerIfNeeded() }, 2000)
            }
        }
    }
}