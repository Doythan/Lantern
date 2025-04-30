package com.example.blemodule.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * BLE 클라이언트 역할을 하는 클래스
 * 다른 기기에 연결하고 데이터를 송수신
 */
class BleClient(private val context: Context, private val bleManager: BleManager) {
    private val TAG = "BleClient"
    
    // 기기에 연결
    fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "기기에 연결 시도: ${device.address}")
        
        // GATT 서버에 연결
        val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
        
        // 연결된 GATT 클라이언트 추가
        bleManager.addGattClient(device, gatt)
    }
    
    // GATT 콜백
    private val gattCallback = object : BluetoothGattCallback() {
        // 연결 상태 변경 시
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val device = gatt.device
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "기기에 연결됨: ${device.address}")
                        // 서비스 검색 시작
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "기기 연결 해제: ${device.address}")
                        // 연결 해제된 GATT 클라이언트 제거
                        bleManager.removeGattClient(device.address)
                    }
                }
            } else {
                Log.e(TAG, "연결 상태 변경 오류: $status")
                gatt.close()
                bleManager.removeGattClient(device.address)
            }
        }
        
        // 서비스 검색 완료 시
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "서비스 검색 완료: ${gatt.device.address}")
                
                // 채팅 서비스 및 특성 찾기
                val service = gatt.getService(BleManager.SERVICE_UUID)
                if (service != null) {
                    Log.d(TAG, "채팅 서비스 발견")
                } else {
                    Log.e(TAG, "채팅 서비스를 찾을 수 없음")
                }
            } else {
                Log.e(TAG, "서비스 검색 실패: $status")
            }
        }
        
        // 특성 읽기 완료 시
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "특성 읽기 완료: ${characteristic.uuid}")
                
                if (characteristic.uuid == BleManager.CHARACTERISTIC_UUID) {
                    val data = characteristic.value
                    if (data != null && data.isNotEmpty()) {
                        // 데이터 처리
                        handleReceivedData(data)
                    }
                }
            } else {
                Log.e(TAG, "특성 읽기 실패: $status")
            }
        }
        
        // 특성 변경 시
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.d(TAG, "특성 변경: ${characteristic.uuid}")
            
            if (characteristic.uuid == BleManager.CHARACTERISTIC_UUID) {
                val data = characteristic.value
                if (data != null && data.isNotEmpty()) {
                    // 데이터 처리
                    handleReceivedData(data)
                }
            }
        }
        
        // 특성 쓰기 완료 시
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "특성 쓰기 완료: ${characteristic.uuid}")
            } else {
                Log.e(TAG, "특성 쓰기 실패: $status")
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
        }
    }
    
    companion object {
        // 데이터 전송
        fun sendData(gatt: BluetoothGatt, data: ByteArray): Boolean {
            // 채팅 서비스 및 특성 찾기
            val service = gatt.getService(BleManager.SERVICE_UUID) ?: return false
            val characteristic = service.getCharacteristic(BleManager.CHARACTERISTIC_UUID) ?: return false
            
            // 데이터 설정
            characteristic.value = data
            
            // 데이터 쓰기
            return gatt.writeCharacteristic(characteristic)
        }
    }
}
