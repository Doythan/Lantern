package com.example.blemodule.data.source.ble

import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.blemodule.utils.UuidConstants

class GattServerManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    private var gattServer: BluetoothGattServer? = null

    fun startServer() {
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            gattServer?.addService(createLanternService())
            Log.d(TAG, "GATT Server started")
        } else {
            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted. Cannot start GATT server.")
        }
    }

    fun stopServer() {
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            gattServer?.close()
            gattServer = null
            Log.d(TAG, "GATT Server stopped")
        } else {
            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted. Cannot stop GATT server.")
        }
    }

    private fun createLanternService(): BluetoothGattService {
        val service = BluetoothGattService(
            UuidConstants.LANTERN_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val writeCharacteristic = BluetoothGattCharacteristic(
            UuidConstants.MESSAGE_WRITE_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val notifyCharacteristic = BluetoothGattCharacteristic(
            UuidConstants.MESSAGE_NOTIFY_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        service.addCharacteristic(writeCharacteristic)
        service.addCharacteristic(notifyCharacteristic)

        return service
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            Log.d(TAG, "Device connection state changed: $device, state: $newState")
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)

            if (characteristic.uuid == UuidConstants.MESSAGE_WRITE_CHARACTERISTIC_UUID) {
                val message = value.toString(Charsets.UTF_8)
                Log.d(TAG, "Received message from ${device.address}: $message")

                if (responseNeeded) {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        gattServer?.sendResponse(device, requestId, android.bluetooth.BluetoothGatt.GATT_SUCCESS, 0, null)
                    } else {
                        Log.e(TAG, "BLUETOOTH_CONNECT permission not granted. Cannot send response.")
                    }
                }
            }
        }

    }

    companion object {
        private const val TAG = "GattServerManager"
    }
}
