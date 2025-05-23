package com.ssafy.lanterns.service.ble.gatt

import android.bluetooth.*
import android.content.Context
import android.util.Log
import com.ssafy.lanterns.config.BleConstants
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * GATT 서버 역할을 수행하는 매니저 클래스
 * 통화 서비스 및 특성을 제공하고, 연결된 클라이언트의 요청을 처리합니다.
 */
class CallGattServerManager private constructor() {
    private val TAG = "LANT_GattServer"
    
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothGattServer: BluetoothGattServer? = null
    private var contextRef: WeakReference<Context>? = null
    
    // 연결된 클라이언트 디바이스 관리
    private val connectedDevices = ConcurrentHashMap<String, BluetoothDevice>()
    
    // 콜백 인터페이스
    private var callback: Callback? = null
    
    // MTU 크기 (기본 값 23, 협상을 통해 변경될 수 있음)
    private var currentMtu = 23
    
    // 싱글톤 패턴 구현
    companion object {
        private var instance: CallGattServerManager? = null
        
        @Synchronized
        fun getInstance(): CallGattServerManager {
            if (instance == null) {
                instance = CallGattServerManager()
            }
            return instance!!
        }
    }
    
    /**
     * GATT 서버 초기화 
     */
    fun initialize(context: Context, callback: Callback): Boolean {
        this.contextRef = WeakReference(context)
        this.callback = callback
        
        try {
            bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            if (bluetoothManager == null) {
                Log.e(TAG, "BluetoothManager 초기화 실패")
                return false
            }
            
            val bluetoothAdapter = bluetoothManager?.adapter
            if (bluetoothAdapter == null) {
                Log.e(TAG, "디바이스가 블루투스를 지원하지 않습니다")
                return false
            }
            
            if (!bluetoothAdapter.isEnabled) {
                Log.e(TAG, "블루투스가 비활성화되어 있습니다")
                return false
            }
            
            Log.i(TAG, "GATT 서버 초기화 성공")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "GATT 서버 초기화 중 예외 발생: ${e.message}", e)
            return false
        }
    }
    
    /**
     * GATT 서버 시작 및 서비스 등록
     */
    fun startServer(): Boolean {
        val context = contextRef?.get() ?: return false
        
        if (bluetoothGattServer != null) {
            Log.i(TAG, "GATT 서버가 이미 시작되어 있습니다")
            return true
        }
        
        try {
            bluetoothGattServer = bluetoothManager?.openGattServer(context, gattServerCallback)
            if (bluetoothGattServer == null) {
                Log.e(TAG, "GATT 서버 시작 실패")
                return false
            }
            
            // 통화 서비스 생성 및 등록
            val callService = createCallService()
            val success = bluetoothGattServer?.addService(callService) ?: false
            
            if (!success) {
                Log.e(TAG, "통화 서비스 등록 실패")
                stopServer()
                return false
            }
            
            Log.i(TAG, "GATT 서버 시작 및 통화 서비스 등록 성공")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "GATT 서버 시작 중 예외 발생: ${e.message}", e)
            stopServer()
            return false
        }
    }
    
    /**
     * 통화 서비스 생성
     */
    private fun createCallService(): BluetoothGattService {
        // 통화 서비스 생성
        val callService = BluetoothGattService(
            BleConstants.CALL_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        
        // 1. 클라이언트->서버 오디오 스트림 특성
        val audioClientToServerCharacteristic = BluetoothGattCharacteristic(
            BleConstants.AUDIO_STREAM_CLIENT_TO_SERVER_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        
        // 2. 서버->클라이언트 오디오 스트림 특성
        val audioServerToClientCharacteristic = BluetoothGattCharacteristic(
            BleConstants.AUDIO_STREAM_SERVER_TO_CLIENT_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        
        // 알림을 위한 디스크립터 추가
        val audioServerToClientConfigDescriptor = BluetoothGattDescriptor(
            BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        audioServerToClientCharacteristic.addDescriptor(audioServerToClientConfigDescriptor)
        
        // 3. 통화 제어 특성 (응답 필요한 Write, Indicate 지원)
        val callControlCharacteristic = BluetoothGattCharacteristic(
            BleConstants.CALL_CONTROL_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
        )
        
        // 알림을 위한 디스크립터 추가
        val callControlConfigDescriptor = BluetoothGattDescriptor(
            BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        callControlCharacteristic.addDescriptor(callControlConfigDescriptor)
        
        // 4. 통화 상태 특성
        val callStateCharacteristic = BluetoothGattCharacteristic(
            BleConstants.CALL_STATE_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        
        // 알림을 위한 디스크립터 추가
        val callStateConfigDescriptor = BluetoothGattDescriptor(
            BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        callStateCharacteristic.addDescriptor(callStateConfigDescriptor)
        
        // 서비스에 특성 추가
        callService.addCharacteristic(audioClientToServerCharacteristic)
        callService.addCharacteristic(audioServerToClientCharacteristic)
        callService.addCharacteristic(callControlCharacteristic)
        callService.addCharacteristic(callStateCharacteristic)
        
        return callService
    }
    
    /**
     * GATT 서버 중지
     */
    fun stopServer() {
        connectedDevices.clear()
        
        if (bluetoothGattServer != null) {
            bluetoothGattServer?.close()
            bluetoothGattServer = null
            Log.i(TAG, "GATT 서버 중지됨")
        }
    }
    
    /**
     * 통화 상태 변경 알림
     */
    fun notifyCallState(callState: Byte): Boolean {
        if (bluetoothGattServer == null || connectedDevices.isEmpty()) {
            Log.d(TAG, "GATT 서버가 없거나 연결된 디바이스가 없어 상태 알림을 보낼 수 없습니다")
            return false
        }
        
        try {
            // 통화 상태 특성 찾기
            val service = bluetoothGattServer?.getService(BleConstants.CALL_SERVICE_UUID)
            val characteristic = service?.getCharacteristic(BleConstants.CALL_STATE_CHARACTERISTIC_UUID)
            
            if (characteristic == null) {
                Log.e(TAG, "통화 상태 특성을 찾을 수 없습니다")
                return false
            }
            
            // 상태 값 설정
            characteristic.setValue(byteArrayOf(callState))
            
            // 모든 연결된 디바이스에 알림
            var notifiedAny = false
            for (device in connectedDevices.values) {
                val success = bluetoothGattServer?.notifyCharacteristicChanged(device, characteristic, false) ?: false
                if (success) {
                    notifiedAny = true
                    Log.d(TAG, "디바이스 ${device.address}에 통화 상태 알림 성공: $callState")
                } else {
                    Log.e(TAG, "디바이스 ${device.address}에 통화 상태 알림 실패")
                }
            }
            
            return notifiedAny
        } catch (e: Exception) {
            Log.e(TAG, "통화 상태 알림 중 예외 발생: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 통화 제어 명령 전송 (Indicate)
     */
    fun sendCallControl(device: BluetoothDevice, command: Byte): Boolean {
        if (bluetoothGattServer == null) {
            Log.e(TAG, "GATT 서버가 없어 명령을 보낼 수 없습니다")
            return false
        }
        
        try {
            // 통화 제어 특성 찾기
            val service = bluetoothGattServer?.getService(BleConstants.CALL_SERVICE_UUID)
            val characteristic = service?.getCharacteristic(BleConstants.CALL_CONTROL_CHARACTERISTIC_UUID)
            
            if (characteristic == null) {
                Log.e(TAG, "통화 제어 특성을 찾을 수 없습니다")
                return false
            }
            
            // 명령 값 설정
            characteristic.setValue(byteArrayOf(command))
            
            // Indicate 전송 (응답 필요)
            val success = bluetoothGattServer?.notifyCharacteristicChanged(device, characteristic, true) ?: false
            
            if (success) {
                Log.d(TAG, "디바이스 ${device.address}에 통화 제어 명령 전송 성공: $command")
            } else {
                Log.e(TAG, "디바이스 ${device.address}에 통화 제어 명령 전송 실패")
            }
            
            return success
        } catch (e: Exception) {
            Log.e(TAG, "통화 제어 명령 전송 중 예외 발생: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 오디오 데이터 전송
     */
    fun sendAudioData(device: BluetoothDevice, audioData: ByteArray): Boolean {
        if (bluetoothGattServer == null) {
            Log.e(TAG, "GATT 서버가 없어 오디오 데이터를 보낼 수 없습니다")
            return false
        }
        
        try {
            // 오디오 스트림 특성 찾기
            val service = bluetoothGattServer?.getService(BleConstants.CALL_SERVICE_UUID)
            val characteristic = service?.getCharacteristic(BleConstants.AUDIO_STREAM_SERVER_TO_CLIENT_CHARACTERISTIC_UUID)
            
            if (characteristic == null) {
                Log.e(TAG, "오디오 스트림 특성을 찾을 수 없습니다")
                return false
            }
            
            // MTU 크기에 맞게 데이터 분할 처리 필요
            val maximumChunkSize = currentMtu - 3 // 3바이트는 ATT 헤더
            
            // 데이터 크기가 MTU 내에 있는 경우
            if (audioData.size <= maximumChunkSize) {
                characteristic.setValue(audioData)
                val success = bluetoothGattServer?.notifyCharacteristicChanged(device, characteristic, false) ?: false
                
                if (!success) {
                    Log.e(TAG, "오디오 데이터 전송 실패: ${device.address}")
                }
                
                return success
            } else {
                // MTU보다 큰 데이터는 청크로 분할하여 전송 (실제 구현에서는 이 로직을 개선할 필요가 있음)
                Log.w(TAG, "오디오 데이터가 MTU 크기보다 큽니다. 청크로 분할 전송이 필요합니다.")
                var sentAll = true
                
                for (i in audioData.indices step maximumChunkSize) {
                    val endIndex = minOf(i + maximumChunkSize, audioData.size)
                    val chunk = audioData.copyOfRange(i, endIndex)
                    
                    characteristic.setValue(chunk)
                    val success = bluetoothGattServer?.notifyCharacteristicChanged(device, characteristic, false) ?: false
                    
                    if (!success) {
                        Log.e(TAG, "청크 오디오 데이터 전송 실패: ${device.address}, 청크: $i")
                        sentAll = false
                        break
                    }
                }
                
                return sentAll
            }
        } catch (e: Exception) {
            Log.e(TAG, "오디오 데이터 전송 중 예외 발생: ${e.message}", e)
            return false
        }
    }
    
    /**
     * GATT 서버 콜백
     */
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connectedDevices[device.address] = device
                    Log.i(TAG, "디바이스가 GATT 서버에 연결됨: ${device.address}")
                    callback?.onDeviceConnected(device)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connectedDevices.remove(device.address)
                    Log.i(TAG, "디바이스가 GATT 서버에서 연결 해제됨: ${device.address}")
                    callback?.onDeviceDisconnected(device)
                }
            } else {
                Log.e(TAG, "연결 상태 변경 오류, 상태: $status, 디바이스: ${device.address}")
                connectedDevices.remove(device.address)
                callback?.onDeviceDisconnected(device)
            }
        }
        
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "GATT 서비스가 성공적으로 추가됨: ${service.uuid}")
                callback?.onServiceAdded(service.uuid)
            } else {
                Log.e(TAG, "GATT 서비스 추가 실패: ${service.uuid}, 상태: $status")
            }
        }
        
        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            Log.i(TAG, "MTU 변경됨: $mtu, 디바이스: ${device.address}")
            currentMtu = mtu
            callback?.onMtuChanged(device, mtu)
        }
        
        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            Log.d(TAG, "특성 읽기 요청: ${characteristic.uuid}, 디바이스: ${device.address}")
            
            // 기본값으로 응답
            bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.value)
        }
        
        override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            Log.d(TAG, "특성 쓰기 요청: ${characteristic.uuid}, 디바이스: ${device.address}, 응답 필요: $responseNeeded")
            
            // 응답이 필요한 경우 (통화 제어 명령)
            if (responseNeeded) {
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
            
            when (characteristic.uuid) {
                BleConstants.AUDIO_STREAM_CLIENT_TO_SERVER_CHARACTERISTIC_UUID -> {
                    // 클라이언트로부터 오디오 데이터 수신
                    callback?.onAudioDataReceived(device, value)
                }
                BleConstants.CALL_CONTROL_CHARACTERISTIC_UUID -> {
                    if (value.isNotEmpty()) {
                        // 통화 제어 명령 수신
                        val command = value[0]
                        callback?.onCallControlReceived(device, command)
                    }
                }
                else -> {
                    Log.w(TAG, "알 수 없는 특성에 대한 쓰기 요청: ${characteristic.uuid}")
                }
            }
        }
        
        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            Log.d(TAG, "디스크립터 읽기 요청: ${descriptor.uuid}, 특성: ${descriptor.characteristic.uuid}")
            
            // CCCD 읽기 요청 처리
            if (descriptor.uuid == BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID) {
                val value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            } else {
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
            }
        }
        
        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            Log.d(TAG, "디스크립터 쓰기 요청: ${descriptor.uuid}, 특성: ${descriptor.characteristic.uuid}")
            
            // CCCD 쓰기 요청 처리 (알림/Indicate 활성화/비활성화)
            if (descriptor.uuid == BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID) {
                // 알림 활성화 요청
                val isEnableNotification = Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                val isEnableIndication = Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                
                if (isEnableNotification || isEnableIndication) {
                    when (descriptor.characteristic.uuid) {
                        BleConstants.AUDIO_STREAM_SERVER_TO_CLIENT_CHARACTERISTIC_UUID -> {
                            Log.i(TAG, "오디오 스트림 알림 활성화: ${device.address}")
                            callback?.onAudioStreamNotificationEnabled(device, true)
                        }
                        BleConstants.CALL_STATE_CHARACTERISTIC_UUID -> {
                            Log.i(TAG, "통화 상태 알림 활성화: ${device.address}")
                            callback?.onCallStateNotificationEnabled(device, true)
                        }
                        BleConstants.CALL_CONTROL_CHARACTERISTIC_UUID -> {
                            Log.i(TAG, "통화 제어 Indicate 활성화: ${device.address}")
                            callback?.onCallControlIndicationEnabled(device, true)
                        }
                    }
                } else {
                    // 알림 비활성화 요청
                    when (descriptor.characteristic.uuid) {
                        BleConstants.AUDIO_STREAM_SERVER_TO_CLIENT_CHARACTERISTIC_UUID -> {
                            Log.i(TAG, "오디오 스트림 알림 비활성화: ${device.address}")
                            callback?.onAudioStreamNotificationEnabled(device, false)
                        }
                        BleConstants.CALL_STATE_CHARACTERISTIC_UUID -> {
                            Log.i(TAG, "통화 상태 알림 비활성화: ${device.address}")
                            callback?.onCallStateNotificationEnabled(device, false)
                        }
                        BleConstants.CALL_CONTROL_CHARACTERISTIC_UUID -> {
                            Log.i(TAG, "통화 제어 Indicate 비활성화: ${device.address}")
                            callback?.onCallControlIndicationEnabled(device, false)
                        }
                    }
                }
                
                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
            } else {
                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                }
            }
        }
    }
    
    /**
     * 콜백 인터페이스
     */
    interface Callback {
        fun onDeviceConnected(device: BluetoothDevice)
        fun onDeviceDisconnected(device: BluetoothDevice)
        fun onServiceAdded(serviceUuid: UUID)
        fun onMtuChanged(device: BluetoothDevice, mtu: Int)
        fun onAudioDataReceived(device: BluetoothDevice, data: ByteArray)
        fun onCallControlReceived(device: BluetoothDevice, command: Byte)
        fun onAudioStreamNotificationEnabled(device: BluetoothDevice, enabled: Boolean)
        fun onCallStateNotificationEnabled(device: BluetoothDevice, enabled: Boolean)
        fun onCallControlIndicationEnabled(device: BluetoothDevice, enabled: Boolean)
    }
} 