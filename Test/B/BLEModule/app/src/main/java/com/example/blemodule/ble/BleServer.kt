package com.example.blemodule.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * BLE 서버 역할을 하는 클래스
 * GATT 서버를 관리하고 다른 기기로부터의 연결 및 데이터 수신을 처리
 */
class BleServer(private val context: Context, private val bleManager: BleManager) {
    private val TAG = "BleServer"
    
    private var gattServer: BluetoothGattServer? = null
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    
    // GATT 서버 시작
    fun startServer() {
        Log.d(TAG, "GATT 서버 시작")
        
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        
        // 서비스 생성
        val service = BluetoothGattService(
            BleManager.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        
        // 특성 생성
        val characteristic = BluetoothGattCharacteristic(
            BleManager.CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or
                    BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        
        // 서비스에 특성 추가
        service.addCharacteristic(characteristic)
        
        // GATT 서버에 서비스 추가
        gattServer?.addService(service)
    }
    
    // GATT 서버 중지
    fun stopServer() {
        Log.d(TAG, "GATT 서버 중지")
        gattServer?.close()
        gattServer = null
    }
    
    // GATT 서버 콜백
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        // 기기 연결 상태 변경 시
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "기기 연결됨: ${device.address}")
                        // 연결된 기기 정보 전송 (시스템 메시지)
                        val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            device.alias ?: device.name ?: "Unknown Device"
                        } else {
                            device.name ?: "Unknown Device"
                        }
                        
                        val systemMessage = BleMessage(
                            senderName = "System",
                            content = "$deviceName 기기가 연결되었습니다.",
                            messageType = MessageType.SYSTEM
                        )
                        
                        bleManager.handleReceivedMessage(systemMessage)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "기기 연결 해제: ${device.address}")
                        // 연결 해제된 기기 정보 전송 (시스템 메시지)
                        val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            device.alias ?: device.name ?: "Unknown Device"
                        } else {
                            device.name ?: "Unknown Device"
                        }
                        
                        val systemMessage = BleMessage(
                            senderName = "System",
                            content = "$deviceName 기기의 연결이 해제되었습니다.",
                            messageType = MessageType.SYSTEM
                        )
                        
                        bleManager.handleReceivedMessage(systemMessage)
                    }
                }
            } else {
                Log.e(TAG, "연결 상태 변경 오류: $status")
            }
        }
        
        // 특성 읽기 요청 시
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.d(TAG, "특성 읽기 요청: ${device.address}")
            
            if (characteristic.uuid == BleManager.CHARACTERISTIC_UUID) {
                // 현재는 빈 데이터 반환
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    ByteArray(0)
                )
            } else {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_FAILURE,
                    0,
                    null
                )
            }
        }
        
        // 특성 쓰기 요청 시
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            Log.d(TAG, "특성 쓰기 요청: ${device.address}")
            
            if (characteristic.uuid == BleManager.CHARACTERISTIC_UUID) {
                // 응답 전송
                if (responseNeeded) {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        value
                    )
                }
                
                // 수신된 데이터 처리
                handleReceivedData(value)
            } else if (responseNeeded) {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_FAILURE,
                    0,
                    null
                )
            }
        }
    }
    
    // 수신된 데이터 처리
    private fun handleReceivedData(data: ByteArray) {
        // 데이터를 BleMessage로 변환
        val message = BleMessage.fromByteArray(data)
        
        // 메시지가 유효한 경우 처리
        message?.let {
            // 메시지 처리
            bleManager.handleReceivedMessage(it)
            
            // 다른 연결된 기기에 메시지 전달 (릴레이)
            if (it.messageType == MessageType.CHAT || it.messageType == MessageType.BROADCAST) {
                val connectedGattClients = bleManager.getConnectedGattClients()
                connectedGattClients.values.forEach { gatt ->
                    BleClient.sendData(gatt, data)
                }
            }
        }
    }
}
