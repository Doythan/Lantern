package com.example.blemodule.data.source.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.blemodule.utils.UuidConstants

class GattClientManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    private var bluetoothGatt: BluetoothGatt? = null

    fun connectToDevice(device: BluetoothDevice) {
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            Log.d(TAG, "Connecting to device: ${device.address}")
        } else {
            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted. Cannot connect to device.")
        }
    }

    fun disconnect() {
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
            Log.d(TAG, "Disconnected from device")
        } else {
            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted. Cannot disconnect.")
        }
    }

    fun sendMessage(message: String) {
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothGatt?.getService(UuidConstants.LANTERN_SERVICE_UUID)
                ?.getCharacteristic(UuidConstants.MESSAGE_WRITE_CHARACTERISTIC_UUID)
                ?.let { characteristic ->
                    characteristic.value = message.toByteArray(Charsets.UTF_8)
                    bluetoothGatt?.writeCharacteristic(characteristic)
                    Log.d(TAG, "Message sent: $message")
                }
        } else {
            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted. Cannot send message.")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Connected to GATT server")
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected from GATT server")
                }
            } else {
                Log.e(TAG, "BLUETOOTH_CONNECT permission not granted. Ignoring connection state change.")
            }
        }


        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            Log.d(TAG, "Services discovered")
            // 여기서 notify characteristic 구독 설정도 나중에 추가 가능
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (characteristic.uuid == UuidConstants.MESSAGE_NOTIFY_CHARACTERISTIC_UUID) {
                val receivedMessage = characteristic.value.toString(Charsets.UTF_8)
                Log.d(TAG, "Received message: $receivedMessage")

                // 받은 메시지 처리
            }
        }
    }

    companion object {
        private const val TAG = "GattClientManager"
    }
}
