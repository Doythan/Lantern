package com.example.blemodule.data.source.ble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.example.blemodule.utils.Constants

/**
 * GATT 서버를 관리하는 매니저 클래스
 */
class GattServerManager(
    private val context: Context,
    private val listener: GattServerListener
) {
    companion object {
        private const val TAG = "GattServerManager"
    }

    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(BluetoothManager::class.java)
    }
    private var gattServer: BluetoothGattServer? = null
    private lateinit var meshCharacteristic: BluetoothGattCharacteristic

    /**
     * GATT 서버 시작
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startServer() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            throw SecurityException("GATT 서버 시작 권한 없음")
        }
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            ?: throw IllegalStateException("GATT 서버 열기 실패")

        val service = BluetoothGattService(
            Constants.MESH_SERVICE_UUID.uuid,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        meshCharacteristic = BluetoothGattCharacteristic(
            Constants.MESH_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or
                    BluetoothGattCharacteristic.PERMISSION_WRITE
        ).apply {
            addDescriptor(
                BluetoothGattDescriptor(
                    Constants.CCCD_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or
                            BluetoothGattDescriptor.PERMISSION_WRITE
                )
            )
        }
        service.addCharacteristic(meshCharacteristic)

        // 서비스 추가 직후 상태 로그
        if (gattServer?.addService(service) == true) {
            Log.d(TAG, "▶ GATT 서비스 추가 완료: ${service.uuid}")
            service.characteristics.forEach { char ->
                Log.d(TAG, "   ▶ 서비스에 포함된 캐릭터리스틱: ${char.uuid} (properties=${char.properties})")
            }
        } else {
            Log.e(TAG, "▶ GATT 서비스 추가 실패: ${service.uuid}")
            stopServer()
            throw IllegalStateException("GATT 서비스 추가 실패")
        }
    }

    /**
     * GATT 서버 중지
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun stopServer() {
        gattServer?.close()
        gattServer = null
        Log.d(TAG, "GATT 서버 닫힘.")
    }

    /**
     * 클라이언트에게 Notification 전송
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun notifyDevice(device: BluetoothDevice, data: ByteArray) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.w(TAG, "notifyDevice: 권한 없음")
            return
        }
        val server = gattServer ?: return
        meshCharacteristic.value = data
        server.notifyCharacteristicChanged(device, meshCharacteristic, false)
        Log.d(TAG, "알림 전송 to ${device.address}")
    }

    /**
     * 권한 확인
     */
    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * GATT 서버 콜백
     */
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(
            device: BluetoothDevice?, status: Int, newState: Int
        ) {
            super.onConnectionStateChange(device, status, newState)
            device ?: return
            when (newState) {
                BluetoothGattServer.STATE_CONNECTED ->
                    Log.d(TAG, "클라이언트 연결됨 - ${device.address}")
                BluetoothGattServer.STATE_DISCONNECTED ->
                    Log.d(TAG, "클라이언트 연결 해제 - ${device.address}")
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?, requestId: Int,
            characteristic: BluetoothGattCharacteristic?, preparedWrite: Boolean,
            responseNeeded: Boolean, offset: Int, value: ByteArray?
        ) {
            device ?: return
            characteristic ?: return
            value ?: return

            if (characteristic.uuid == Constants.MESH_CHARACTERISTIC_UUID) {
                Log.d(TAG, "쓰기 요청 수신 from ${device.address}: ${String(value)}")
                listener.onWrite(device, value)
            }
            if (responseNeeded) {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    value
                )
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?, requestId: Int,
            descriptor: BluetoothGattDescriptor?, preparedWrite: Boolean,
            responseNeeded: Boolean, offset: Int, value: ByteArray?
        ) {
            device ?: return
            descriptor ?: return
            value ?: return

            if (descriptor.uuid == Constants.CCCD_UUID) {
                Log.d(TAG, "CCCD 쓰기 완료: ${device.address}")
            }
            if (responseNeeded) {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    value
                )
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            super.onServiceAdded(status, service)
            if (status == BluetoothGatt.GATT_SUCCESS && service != null) {
                Log.d(TAG, "▶ onServiceAdded: 성공 – service=${service.uuid}")
                service.characteristics.forEach { c ->
                    Log.d(TAG, "   • characteristic in server: ${c.uuid} (props=${c.properties})")
                }
            } else {
                Log.e(TAG, "▶ onServiceAdded: 실패(status=$status)")
            }
        }
    }
}
