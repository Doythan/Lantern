package com.ssafy.lanterns.data.source.ble.gatt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.ssafy.lanterns.data.source.ble.audio.AudioManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class AudioGattClientManager(
    private val context: Context,
    private val onData: (String, ByteArray) -> Unit,
    private val onConnected: (String) -> Unit = {},
    private val onDisconnected: (String) -> Unit = {}
) {
    companion object {
        private const val REQUEST_MTU = 512
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val RECONNECT_DELAY_MS = 1000L
        private val SERVICE_UUID = UUID.fromString("0000beef-0000-1000-8000-00805f9b34fb")
        private val CHAR_UUID    = UUID.fromString("0000cafe-0000-1000-8000-00805f9b34fb")
        private val DESC_UUID    = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val handler = Handler(Looper.getMainLooper())
    private val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
        ?.adapter
    private val clients = ConcurrentHashMap<String, BluetoothGatt>()
    private val reconnectAttempts = ConcurrentHashMap<String, Int>()
    private val pendingOperations = ConcurrentHashMap<String, Boolean>()
    private val isSubscribed = ConcurrentHashMap<String, Boolean>()

    fun connectToDevice(device: BluetoothDevice) {
        val addr = device.address

        if (clients.contains(addr)) {
            return
        }

        if (adapter?.isEnabled != true) {
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        try {
            reconnectAttempts[addr] = 0
            pendingOperations[addr] = false
            isSubscribed[addr] = false

            val gatt = device.connectGatt(context, false, gattCb, BluetoothDevice.TRANSPORT_LE)
            clients[addr] = gatt
        } catch (e: Exception) {
            // Connection initiation error
        }
    }

    fun sendAudioData(data: ByteArray) {
        if (clients.isEmpty()) {
            return
        }

        clients.entries.forEach { (addr, gatt) ->
            try {
                val service = gatt.getService(SERVICE_UUID)
                if (service == null) {
                    return@forEach
                }

                val characteristic = service.getCharacteristic(CHAR_UUID)
                if (characteristic == null) {
                    return@forEach
                }

                if (pendingOperations[addr] == true) {
                    handler.postDelayed({ sendDataToDevice(gatt, characteristic, data) }, 100)
                } else {
                    sendDataToDevice(gatt, characteristic, data)
                }
            } catch (e: Exception) {
                // Error preparing to send data
            }
        }
    }

    private fun sendDataToDevice(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        val addr = gatt.device.address
        pendingOperations[addr] = true

        try {
            val typeString = when {
                data.isEmpty() -> "빈 데이터"
                data[0] == AudioManager.TYPE_CALL_REQUEST -> "통화 요청"
                data[0] == AudioManager.TYPE_CALL_ACCEPT -> "통화 수락"
                data[0] == AudioManager.TYPE_CALL_END -> "통화 종료"
                data[0] == AudioManager.TYPE_AUDIO_DATA -> "오디오 데이터"
                data[0] == AudioManager.TYPE_HEARTBEAT -> "하트비트"
                data[0] == AudioManager.TYPE_HEARTBEAT_ACK -> "하트비트 응답"
                else -> "알 수 없음(${data[0]})"
            }

            // 중요: 오디오 데이터 로깅 최소화
            if (data[0] != AudioManager.TYPE_AUDIO_DATA) {
                Log.d("AudioGattClient", "데이터 전송 시작: $typeString, 주소: $addr")
            }

            // 오디오 데이터는 항상 WRITE_TYPE_NO_RESPONSE 사용 - 변경 없음
            if (data.isNotEmpty() && data[0] == AudioManager.TYPE_AUDIO_DATA) {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                // 다른 메시지는 WRITE_TYPE_DEFAULT 사용 - 변경 없음
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }

            characteristic.value = data
            val success = gatt.writeCharacteristic(characteristic)

            // 중요: pendingOperations 가용 시간 최적화
            // 오디오 데이터는 50ms, 다른 데이터는 200ms
            val delayMs = if (data.isNotEmpty() && data[0] == AudioManager.TYPE_AUDIO_DATA) 50L else 200L
            handler.postDelayed({
                pendingOperations[addr] = false
            }, delayMs)
        } catch (e: Exception) {
            Log.e("AudioGattClient", "데이터 전송 중 오류 발생: ${e.message}", e)
            pendingOperations[addr] = false
        }
    }

    fun isFullyConnected(address: String): Boolean {
        return clients.containsKey(address) && isSubscribed[address] == true
    }

    fun isConnected(address: String): Boolean {
        return clients.containsKey(address)
    }

    fun getConnectedDevices(): List<String> {
        return clients.keys().toList()
    }

    fun checkConnectionStatus() {
        clients.forEach { (addr, gatt) ->
            if (isSubscribed[addr] == false) {
                try {
                    gatt.discoverServices()
                } catch (e: Exception) {
                    // Error re-discovering services
                }
            }
        }
    }

    fun disconnectDevice(address: String) {
        clients[address]?.let { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                // Error during gatt disconnect/close
            } finally {
                clients.remove(address)
                reconnectAttempts.remove(address)
                pendingOperations.remove(address)
                isSubscribed.remove(address)
            }
        }
    }

    fun disconnectAll() {
        clients.forEach { (addr, gatt) ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                // Error disconnecting individual gatt
            }
        }
        clients.clear()
        reconnectAttempts.clear()
        pendingOperations.clear()
        isSubscribed.clear()
    }

    fun getBluetoothDevice(address: String): BluetoothDevice? {
        return adapter?.getRemoteDevice(address)
    }

    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val addr = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    reconnectAttempts[addr] = 0
                    try {
                        gatt.requestMtu(REQUEST_MTU)
                    } catch (e: Exception) {
                        gatt.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    handleDisconnect(gatt)
                }
            } else {
                handleDisconnect(gatt)
            }
        }

        private fun handleDisconnect(gatt: BluetoothGatt) {
            val addr = gatt.device.address
            clients.remove(addr)
            pendingOperations.remove(addr)
            isSubscribed[addr] = false
            try {
                gatt.close()
            } catch (e: Exception) {
                // Error closing gatt
            }
            onDisconnected(addr)

            val attempts = reconnectAttempts.getOrDefault(addr, 0)
            if (attempts < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempts[addr] = attempts + 1
                handler.postDelayed({
                    connectToDevice(gatt.device)
                }, RECONNECT_DELAY_MS * (attempts + 1))
            } else {
                reconnectAttempts.remove(addr)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt.discoverServices()
            } else {
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                setupNotifications(gatt)
            } else {
                handler.postDelayed({ gatt.discoverServices() }, 1000)
            }
        }

        private fun setupNotifications(gatt: BluetoothGatt) {
            val addr = gatt.device.address
            try {
                val service = gatt.getService(SERVICE_UUID)
                if (service == null) {
                    rediscoverServices(gatt)
                    return
                }

                val characteristic = service.getCharacteristic(CHAR_UUID)
                if (characteristic == null) {
                    rediscoverServices(gatt)
                    return
                }

                val result = gatt.setCharacteristicNotification(characteristic, true)
                if (!result) {
                    rediscoverServices(gatt)
                    return
                }

                val descriptor = characteristic.getDescriptor(DESC_UUID)
                if (descriptor == null) {
                    rediscoverServices(gatt)
                    return
                }

                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                pendingOperations[addr] = true

                handler.postDelayed({
                    try {
                        val writeResult = gatt.writeDescriptor(descriptor)
                        handler.postDelayed({
                            pendingOperations[addr] = false
                            if (!writeResult) {
                                setupNotifications(gatt)
                            }
                        }, 300)
                    } catch (e: Exception) {
                        pendingOperations[addr] = false
                        handler.postDelayed({ setupNotifications(gatt) }, 1000)
                    }
                }, 200)
            } catch (e: Exception) {
                pendingOperations[addr] = false
                handler.postDelayed({ setupNotifications(gatt) }, 1000)
            }
        }

        private fun rediscoverServices(gatt: BluetoothGatt) {
            handler.postDelayed({
                try {
                    gatt.discoverServices()
                } catch (e: Exception) {
                    // Error during rediscover
                }
            }, 2000)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val addr = gatt.device.address
            pendingOperations[addr] = false

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (descriptor.uuid == DESC_UUID) {
                    isSubscribed[addr] = true
                    onConnected(addr)
                }
            } else {
                handler.postDelayed({ setupNotifications(gatt) }, 1000)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == CHAR_UUID) {
                val data = characteristic.value ?: return
                onData(gatt.device.address, data)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            // No specific action here as sendDataToDevice handles its own pendingOperations flag
        }
    }
}