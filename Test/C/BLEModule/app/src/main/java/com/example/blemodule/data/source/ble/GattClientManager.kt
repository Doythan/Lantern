package com.example.blemodule.data.source.ble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.example.blemodule.utils.Constants
import java.nio.charset.StandardCharsets

/**
 * GATT 클라이언트 콜백을 정의하는 인터페이스
 */
interface GattClientListener {
    fun onConnected(device: BluetoothDevice)
    fun onDisconnected(device: BluetoothDevice)
    fun onServicesDiscovered(device: BluetoothDevice)
    fun onCharacteristicChanged(device: BluetoothDevice, data: ByteArray)
    fun onCharacteristicWrite(device: BluetoothDevice, status: Int)
}

/**
 * GATT 클라이언트를 관리하는 매니저 클래스
 */
class GattClientManager(
    private val context: Context,
    private val listener: GattClientListener
) {
    companion object {
        private const val TAG = "GattClientManager"
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var meshCharacteristic: BluetoothGattCharacteristic? = null

    /** BLE 디바이스에 GATT 연결 요청 */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BluetoothDevice) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.w(TAG, "connect: 권한 없음")
            return
        }
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        Log.d(TAG, "connectGatt 호출: ${device.address}")
    }

    /** GATT 연결 종료 */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        meshCharacteristic = null
        Log.d(TAG, "disconnect & close")
    }

    /** 서버로 특성 쓰기 */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun write(device: BluetoothDevice, data: ByteArray) {
        val text = String(data, StandardCharsets.UTF_8)
        Log.d(TAG, "▶ write() 호출: device=${device.address}, data=$text")

        val char = meshCharacteristic
        val gatt = bluetoothGatt
        if (char == null) {
            Log.w(TAG, "   meshCharacteristic is null, write 중단")
            return
        }
        if (gatt == null) {
            Log.w(TAG, "   bluetoothGatt is null, write 중단")
            return
        }

        char.value = data
        val success = gatt.writeCharacteristic(char)
        Log.d(TAG, "   writeCharacteristic 요청: success=$success, uuid=${char.uuid}")
    }

    /** 알림(Notification) 활성화 */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun enableNotifications(device1: BluetoothDevice) {
        val gatt = bluetoothGatt
        val char = meshCharacteristic
        if (gatt == null || char == null) {
            Log.w(TAG, "enableNotifications: GATT 또는 Characteristic 이 null")
            return
        }

        Log.d(TAG, "▶ enableNotifications 호출")
        gatt.setCharacteristicNotification(char, true)
        char.getDescriptor(Constants.CCCD_UUID)?.let { descriptor ->
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, descriptor.value)
            } else {
                gatt.writeDescriptor(descriptor)
            }
            Log.d(TAG, "   CCCD 쓰기 요청: ${descriptor.uuid}")
        } ?: Log.w(TAG, "   CCCD 디스크립터(null), 알림 설정 불가")
    }

    /** 권한 확인 헬퍼 */
    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** GATT 콜백 구현 */
    @Suppress("DEPRECATION")
    private val gattCallback = object : BluetoothGattCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            val device = gatt.device
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d(TAG, "GATT 연결됨 - ${device.address}")
                listener.onConnected(device)
                gatt.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.d(TAG, "GATT 연결 해제 - ${device.address}")
                listener.onDisconnected(device)
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            val device = gatt.device

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "서비스 발견 성공 - ${device.address}")

                // (0) 실제로 발견된 서비스/characteristic 모두 찍어보기
                gatt.services.forEach { svc: BluetoothGattService ->
                    Log.d(TAG, "   ▶ 서비스: ${svc.uuid}")
                    svc.characteristics.forEach { c ->
                        Log.d(TAG, "       ▶ 캐릭터리스틱: ${c.uuid}")
                    }
                }

                // (1) 우리가 정의한 MESH_SERVICE_UUID 로 Service 조회
                val svc = gatt.getService(Constants.MESH_SERVICE_UUID.uuid)
                meshCharacteristic = svc?.getCharacteristic(Constants.MESH_CHARACTERISTIC_UUID)

                if (meshCharacteristic != null) {
                    Log.d(TAG, "   ✔ meshCharacteristic 초기화: ${meshCharacteristic!!.uuid}")
                    // (2) 초기화 성공 후 알림 활성화
                    enableNotifications(device)
                } else {
                    Log.w(TAG, "   ✘ meshCharacteristic null — Constants UUID 확인 필요")
                }

                // (3) 호출자에게 콜백
                listener.onServicesDiscovered(device)
            } else {
                Log.e(TAG, "서비스 발견 실패: status=$status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (characteristic.uuid == Constants.MESH_CHARACTERISTIC_UUID) {
                val data = characteristic.value ?: return
                Log.d(TAG, "알림 수신: ${String(data, StandardCharsets.UTF_8)}")
                listener.onCharacteristicChanged(gatt.device, data)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            Log.d(TAG, "쓰기 완료: status=$status")
            listener.onCharacteristicWrite(gatt.device, status)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            Log.d(TAG, "Descriptor 쓰기 완료: status=$status, uuid=${descriptor.uuid}")
        }
    }
}
