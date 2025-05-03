package com.example.blemodule.ble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat

/**
 * BLE 서버 역할을 하는 클래스
 * GATT 서버를 관리하고 다른 기기로부터의 연결 및 데이터 수신을 처리
 */
class BleServer(private val context: Context, private val bleManager: BleManager) {
    private val TAG = "BleServer"
    
    private var gattServer: BluetoothGattServer? = null
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    
    // 연결된 기기 목록 (서버 측에서 관리)
    private val connectedDevices = mutableSetOf<BluetoothDevice>()
    
    // 권한 확인
    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    // GATT 서버 시작
    fun startServer() {
        Log.d(TAG, "GATT 서버 시작")
        
        // 권한 확인
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "블루투스 권한이 없습니다.")
            return
        }
        
        try {
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
        } catch (e: SecurityException) {
            Log.e(TAG, "GATT 서버 시작 중 권한 오류: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "GATT 서버 시작 중 오류: ${e.message}")
        }
    }
    
    // GATT 서버 중지
    fun stopServer() {
        Log.d(TAG, "GATT 서버 중지")
        
        // 권한 확인
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "블루투스 권한이 없습니다.")
            return
        }
        
        try {
            // 연결된 모든 기기 연결 해제
            synchronized(connectedDevices) {
                connectedDevices.forEach { device ->
                    try {
                        gattServer?.cancelConnection(device)
                    } catch (e: SecurityException) {
                        Log.e(TAG, "기기 연결 해제 중 권한 오류: ${e.message}")
                    }
                }
                connectedDevices.clear()
            }
            
            gattServer?.close()
            gattServer = null
        } catch (e: SecurityException) {
            Log.e(TAG, "GATT 서버 중지 중 권한 오류: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "GATT 서버 중지 중 오류: ${e.message}")
        }
    }
    
    // 서버를 통해 메시지 브로드캐스트
    fun broadcastMessage(data: ByteArray) {
        Log.d(TAG, "서버를 통해 메시지 브로드캐스트: 연결된 기기 수: ${connectedDevices.size}")
        
        // 권한 확인
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "블루투스 권한이 없습니다.")
            return
        }
        
        synchronized(connectedDevices) {
            connectedDevices.forEach { device ->
                try {
                    // 서비스 및 특성 가져오기
                    val service = gattServer?.getService(BleManager.SERVICE_UUID)
                    val characteristic = service?.getCharacteristic(BleManager.CHARACTERISTIC_UUID)
                    
                    if (characteristic != null) {
                        // 특성 값 설정
                        characteristic.value = data
                        
                        // 연결된 기기에 알림 전송
                        val success = gattServer?.notifyCharacteristicChanged(device, characteristic, false)
                        var resultText = "실패"
                        if (success == true) {
                            resultText = "성공"
                        }
                        Log.d(TAG, "서버 알림 전송 $resultText: ${device.address}")
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "메시지 브로드캐스트 권한 오류: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "메시지 브로드캐스트 오류: ${e.message}")
                }
            }
        }
    }
    
    // GATT 서버 콜백
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        // 기기 연결 상태 변경 시
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            try {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            Log.d(TAG, "기기 연결됨: ${device.address}")
                            
                            // 연결된 기기 목록에 추가
                            synchronized(connectedDevices) {
                                connectedDevices.add(device)
                            }
                            
                            // 서버 측에서도 연결된 기기를 BleManager의 목록에 추가
                            bleManager.addConnectedDevice(device)
                            
                            // 연결된 기기 정보 전송 (시스템 메시지)
                            var deviceName = "알 수 없는 기기"
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                try {
                                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                        deviceName = device.name ?: "알 수 없는 기기"
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "기기 이름 가져오기 오류: ${e.message}")
                                }
                            } else {
                                try {
                                    deviceName = device.name ?: "알 수 없는 기기"
                                } catch (e: Exception) {
                                    Log.e(TAG, "기기 이름 가져오기 오류: ${e.message}")
                                }
                            }
                            
                            val systemMessage = BleMessage(
                                senderName = "System",
                                content = "$deviceName 기기가 연결되었습니다.",
                                messageType = MessageType.SYSTEM
                            )
                            
                            // 중복 메시지 방지를 위해 ID 등록
                            if (bleManager.isNewMessage(systemMessage)) {
                                bleManager.handleReceivedMessage(systemMessage)
                            }
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            Log.d(TAG, "기기 연결 해제: ${device.address}")
                            
                            // 연결된 기기 목록에서 제거
                            synchronized(connectedDevices) {
                                connectedDevices.remove(device)
                            }
                            
                            // 연결 해제된 기기 정보 전송 (시스템 메시지)
                            var deviceName = "알 수 없는 기기"
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                try {
                                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                        deviceName = device.name ?: "알 수 없는 기기"
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "기기 이름 가져오기 오류: ${e.message}")
                                }
                            } else {
                                try {
                                    deviceName = device.name ?: "알 수 없는 기기"
                                } catch (e: Exception) {
                                    Log.e(TAG, "기기 이름 가져오기 오류: ${e.message}")
                                }
                            }
                            
                            val systemMessage = BleMessage(
                                senderName = "System",
                                content = "$deviceName 기기의 연결이 해제되었습니다.",
                                messageType = MessageType.SYSTEM
                            )
                            
                            // 중복 메시지 방지를 위해 ID 등록
                            if (bleManager.isNewMessage(systemMessage)) {
                                bleManager.handleReceivedMessage(systemMessage)
                            }
                            
                            // BleManager에서도 연결 해제 처리
                            bleManager.removeGattClient(device.address)
                        }
                    }
                } else {
                    Log.e(TAG, "연결 상태 변경 오류: $status")
                    
                    // 오류 발생 시 연결된 기기 목록에서 제거
                    synchronized(connectedDevices) {
                        connectedDevices.remove(device)
                    }
                    
                    // BleManager에서도 연결 해제 처리
                    bleManager.removeGattClient(device.address)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "연결 상태 변경 중 권한 오류: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "연결 상태 변경 중 오류: ${e.message}")
            }
        }
        
        // 특성 읽기 요청 시
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            try {
                Log.d(TAG, "특성 읽기 요청: ${device.address}")
                
                if (characteristic.uuid == BleManager.CHARACTERISTIC_UUID) {
                    // 현재는 빈 데이터 반환
                    val server = gattServer
                    if (server != null) {
                        server.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            ByteArray(0)
                        )
                    } else {
                        Log.e(TAG, "GATT 서버가 null입니다: 응답을 보낼 수 없습니다.")
                    }
                } else {
                    val server = gattServer
                    if (server != null) {
                        server.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            0,
                            null
                        )
                    } else {
                        Log.e(TAG, "GATT 서버가 null입니다: 실패 응답을 보낼 수 없습니다.")
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "특성 읽기 요청 처리 중 권한 오류: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "특성 읽기 요청 처리 중 오류: ${e.message}")
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
            try {
                Log.d(TAG, "특성 쓰기 요청: ${device.address}")
                
                if (characteristic.uuid == BleManager.CHARACTERISTIC_UUID) {
                    // 응답 전송
                    if (responseNeeded) {
                        val server = gattServer
                        if (server != null) {
                            server.sendResponse(
                                device,
                                requestId,
                                BluetoothGatt.GATT_SUCCESS,
                                0,
                                value
                            )
                        } else {
                            Log.e(TAG, "GATT 서버가 null입니다: 응답을 보낼 수 없습니다.")
                        }
                    }
                    
                    // 수신된 데이터 처리
                    handleReceivedData(value)
                } else if (responseNeeded) {
                    val server = gattServer
                    if (server != null) {
                        server.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            0,
                            null
                        )
                    } else {
                        Log.e(TAG, "GATT 서버가 null입니다: 실패 응답을 보낼 수 없습니다.")
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "특성 쓰기 요청 처리 중 권한 오류: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "특성 쓰기 요청 처리 중 오류: ${e.message}")
            }
        }
    }
    
    // 수신된 데이터 처리
    private fun handleReceivedData(data: ByteArray) {
        // 데이터를 BleMessage로 변환
        val message = BleMessage.fromByteArray(data)
        
        // 메시지가 유효한 경우 처리
        message?.let {
            // 메시지 처리 (중복 메시지 확인 로직 추가)
            if (bleManager.isNewMessage(it)) {
                // 새 메시지인 경우에만 처리
                Log.d(TAG, "서버에서 새 메시지 수신: ${it.senderName}: ${it.content}")
                bleManager.handleReceivedMessage(it)
                
                // 다른 연결된 기기에 메시지 전달 (릴레이)
                if (it.messageType == MessageType.CHAT || it.messageType == MessageType.BROADCAST) {
                    // 서버에 연결된 다른 기기들에게 메시지 전달
                    Log.d(TAG, "서버에서 메시지 릴레이 시작: 연결된 기기 수: ${connectedDevices.size}")
                    broadcastMessage(data)
                }
            } else {
                Log.d(TAG, "서버에서 중복 메시지 무시: ${it.id}")
            }
        }
    }
}
