package com.ssafy.lanterns.service.ble.gatt

import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ssafy.lanterns.config.BleConstants
import java.lang.ref.WeakReference
import java.util.*

/**
 * GATT 클라이언트 역할을 수행하는 매니저 클래스
 * 원격 GATT 서버에 연결하여 통화 서비스 및 특성을 활용합니다.
 */
class CallGattClientManager private constructor() {
    private val TAG = "LANT_GattClient"
    
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var contextRef: WeakReference<Context>? = null
    
    // 연결 관련 상태
    @Volatile private var isConnecting = false
    @Volatile private var isConnected = false
    private var currentDeviceAddress: String? = null
    private var targetDeviceAddress: String? = null
    
    // 콜백 인터페이스
    private var callback: Callback? = null
    
    // 연결 재시도 관련
    private val handler = Handler(Looper.getMainLooper())
    private var connectRetryCount = 0
    private val MAX_RETRY_COUNT = 3
    private val CONNECTION_TIMEOUT_MS = 10000L
    private var connectTimeoutRunnable: Runnable? = null
    
    // 서비스 및 특성 캐싱
    private var callService: BluetoothGattService? = null
    private var audioClientToServerCharacteristic: BluetoothGattCharacteristic? = null
    private var audioServerToClientCharacteristic: BluetoothGattCharacteristic? = null
    private var callControlCharacteristic: BluetoothGattCharacteristic? = null
    private var callStateCharacteristic: BluetoothGattCharacteristic? = null
    
    // MTU 크기 (기본 값 23, 협상을 통해 변경될 수 있음)
    private var currentMtu = 23
    
    // 싱글톤 패턴 구현
    companion object {
        private var instance: CallGattClientManager? = null
        
        @Synchronized
        fun getInstance(): CallGattClientManager {
            if (instance == null) {
                instance = CallGattClientManager()
            }
            return instance!!
        }
    }
    
    /**
     * GATT 클라이언트 초기화
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
            
            Log.i(TAG, "GATT 클라이언트 초기화 성공")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "GATT 클라이언트 초기화 중 예외 발생: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 특정 기기에 연결
     */
    fun connect(deviceAddress: String): Boolean {
        if (isConnecting) {
            Log.i(TAG, "이미 연결 중입니다: $targetDeviceAddress")
            return false
        }
        
        if (isConnected && deviceAddress == currentDeviceAddress) {
            Log.i(TAG, "이미 연결되어 있습니다: $deviceAddress")
            return true
        }
        
        val context = contextRef?.get() ?: return false
        val bluetoothAdapter = bluetoothManager?.adapter ?: return false
        
        // 이전 연결 정리
        disconnect()
        
        try {
            // 주소로 원격 디바이스 가져오기
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            if (device == null) {
                Log.e(TAG, "알 수 없는 디바이스 주소: $deviceAddress")
                return false
            }
            
            // 연결 상태 초기화
            isConnecting = true
            targetDeviceAddress = deviceAddress
            connectRetryCount = 0
            
            Log.i(TAG, "GATT 연결 시도: $deviceAddress")
            
            // 연결 시도
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            
            // 연결 타임아웃 설정
            scheduleConnectionTimeout()
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "GATT 연결 중 예외 발생: ${e.message}", e)
            isConnecting = false
            targetDeviceAddress = null
            return false
        }
    }
    
    /**
     * 연결 타임아웃 설정
     */
    private fun scheduleConnectionTimeout() {
        cancelConnectionTimeout()
        
        connectTimeoutRunnable = Runnable {
            if (isConnecting && !isConnected) {
                Log.e(TAG, "GATT 연결 타임아웃: $targetDeviceAddress")
                
                // 재시도 또는 실패 처리
                if (connectRetryCount < MAX_RETRY_COUNT) {
                    connectRetryCount++
                    Log.i(TAG, "GATT 연결 재시도 (${connectRetryCount}/${MAX_RETRY_COUNT}): $targetDeviceAddress")
                    
                    // 연결 해제 후 재시도
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    
                    // 잠시 대기 후 재시도
                    handler.postDelayed({
                        targetDeviceAddress?.let { connect(it) }
                    }, 1000)
                } else {
                    // 최대 재시도 횟수 초과
                    Log.e(TAG, "최대 재시도 횟수 초과, 연결 실패: $targetDeviceAddress")
                    disconnect()
                    callback?.onConnectionFailed()
                }
            }
        }.also {
            handler.postDelayed(it, CONNECTION_TIMEOUT_MS)
        }
    }
    
    /**
     * 연결 타임아웃 취소
     */
    private fun cancelConnectionTimeout() {
        connectTimeoutRunnable?.let {
            handler.removeCallbacks(it)
            connectTimeoutRunnable = null
        }
    }
    
    /**
     * 연결 해제
     */
    fun disconnect() {
        cancelConnectionTimeout()
        
        // 특성 및 서비스 캐시 초기화
        audioClientToServerCharacteristic = null
        audioServerToClientCharacteristic = null
        callControlCharacteristic = null
        callStateCharacteristic = null
        callService = null
        
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt?.disconnect()
                bluetoothGatt?.close()
            } catch (e: Exception) {
                Log.e(TAG, "GATT 연결 해제 중 예외 발생: ${e.message}", e)
            } finally {
                bluetoothGatt = null
            }
            
            Log.i(TAG, "GATT 연결 해제됨")
        }
        
        isConnecting = false
        isConnected = false
        currentDeviceAddress = null
        targetDeviceAddress = null
    }
    
    /**
     * 특성 캐싱 및 알림 활성화
     */
    private fun cacheCharacteristicsAndEnableNotifications() {
        if (bluetoothGatt == null || callService == null) {
            Log.e(TAG, "GATT 또는 통화 서비스가 null입니다")
            return
        }
        
        // 특성 캐싱
        audioClientToServerCharacteristic = callService?.getCharacteristic(BleConstants.AUDIO_STREAM_CLIENT_TO_SERVER_CHARACTERISTIC_UUID)
        audioServerToClientCharacteristic = callService?.getCharacteristic(BleConstants.AUDIO_STREAM_SERVER_TO_CLIENT_CHARACTERISTIC_UUID)
        callControlCharacteristic = callService?.getCharacteristic(BleConstants.CALL_CONTROL_CHARACTERISTIC_UUID)
        callStateCharacteristic = callService?.getCharacteristic(BleConstants.CALL_STATE_CHARACTERISTIC_UUID)
        
        if (audioServerToClientCharacteristic == null || callControlCharacteristic == null || callStateCharacteristic == null) {
            Log.e(TAG, "필수 특성을 찾을 수 없습니다")
            return
        }
        
        // 오디오 스트림 알림 활성화
        enableCharacteristicNotification(audioServerToClientCharacteristic!!, false)
        
        // 통화 제어 Indicate 활성화
        enableCharacteristicNotification(callControlCharacteristic!!, true)
        
        // 통화 상태 알림 활성화
        enableCharacteristicNotification(callStateCharacteristic!!, false)
    }
    
    /**
     * 특성 알림 활성화
     */
    private fun enableCharacteristicNotification(characteristic: BluetoothGattCharacteristic, useIndication: Boolean): Boolean {
        val bluetoothGatt = this.bluetoothGatt ?: return false
        
        // 특성의 알림 활성화
        if (!bluetoothGatt.setCharacteristicNotification(characteristic, true)) {
            Log.e(TAG, "특성 알림 활성화 실패: ${characteristic.uuid}")
            return false
        }
        
        // 디스크립터 값 설정 (알림 또는 Indicate)
        val descriptor = characteristic.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
        if (descriptor != null) {
            descriptor.value = if (useIndication) {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }
            
            if (!bluetoothGatt.writeDescriptor(descriptor)) {
                Log.e(TAG, "디스크립터 쓰기 실패: ${characteristic.uuid}")
                return false
            }
        } else {
            Log.e(TAG, "CCCD 디스크립터를 찾을 수 없음: ${characteristic.uuid}")
            return false
        }
        
        return true
    }
    
    /**
     * MTU 요청
     */
    fun requestMtu(mtu: Int): Boolean {
        if (!isConnected || bluetoothGatt == null) {
            Log.e(TAG, "GATT가 연결되어 있지 않아 MTU 요청을 할 수 없습니다")
            return false
        }
        
        return try {
            Log.i(TAG, "MTU 요청: $mtu")
            bluetoothGatt?.requestMtu(mtu) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "MTU 요청 중 예외 발생: ${e.message}", e)
            false
        }
    }
    
    /**
     * 오디오 데이터 전송 (클라이언트 -> 서버)
     */
    fun sendAudioData(audioData: ByteArray): Boolean {
        if (!isConnected || bluetoothGatt == null || audioClientToServerCharacteristic == null) {
            Log.e(TAG, "GATT가 연결되어 있지 않거나 오디오 특성이 null입니다")
            return false
        }
        
        try {
            // MTU 크기에 맞게 데이터 분할 처리 필요
            val maximumChunkSize = currentMtu - 3 // 3바이트는 ATT 헤더
            
            // 데이터 크기가 MTU 내에 있는 경우
            if (audioData.size <= maximumChunkSize) {
                audioClientToServerCharacteristic?.value = audioData
                // 응답 없는 쓰기 사용 (지연 최소화)
                val success = bluetoothGatt?.writeCharacteristic(
                    audioClientToServerCharacteristic!!, 
                    audioData,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                ) ?: false
                
                if (!success) {
                    Log.e(TAG, "오디오 데이터 전송 실패")
                }
                
                return success
            } else {
                // MTU보다 큰 데이터는 청크로 분할하여 전송
                Log.w(TAG, "오디오 데이터가 MTU 크기보다 큽니다. 청크로 분할 전송이 필요합니다.")
                var sentAll = true
                
                for (i in audioData.indices step maximumChunkSize) {
                    val endIndex = minOf(i + maximumChunkSize, audioData.size)
                    val chunk = audioData.copyOfRange(i, endIndex)
                    
                    // 응답 없는 쓰기 사용 (지연 최소화)
                    val success = bluetoothGatt?.writeCharacteristic(
                        audioClientToServerCharacteristic!!, 
                        chunk,
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    ) ?: false
                    
                    if (!success) {
                        Log.e(TAG, "청크 오디오 데이터 전송 실패, 청크: $i")
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
     * 통화 제어 명령 전송
     */
    fun sendCallControl(command: Byte): Boolean {
        if (!isConnected || bluetoothGatt == null || callControlCharacteristic == null) {
            Log.e(TAG, "GATT가 연결되어 있지 않거나 통화 제어 특성이 null입니다")
            return false
        }
        
        try {
            Log.i(TAG, "통화 제어 명령 전송: $command")
            val commandData = byteArrayOf(command)
            
            // 응답이 필요한 쓰기 사용 (신뢰성 확보)
            val success = bluetoothGatt?.writeCharacteristic(
                callControlCharacteristic!!, 
                commandData,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) ?: false
            
            if (!success) {
                Log.e(TAG, "통화 제어 명령 전송 실패: $command")
            }
            
            return success
        } catch (e: Exception) {
            Log.e(TAG, "통화 제어 명령 전송 중 예외 발생: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 통화 상태 읽기
     */
    fun readCallState(): Boolean {
        if (!isConnected || bluetoothGatt == null || callStateCharacteristic == null) {
            Log.e(TAG, "GATT가 연결되어 있지 않거나 통화 상태 특성이 null입니다")
            return false
        }
        
        try {
            Log.i(TAG, "통화 상태 읽기 요청")
            return bluetoothGatt?.readCharacteristic(callStateCharacteristic!!) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "통화 상태 읽기 중 예외 발생: ${e.message}", e)
            return false
        }
    }
    
    /**
     * GATT 콜백
     */
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "GATT 연결 성공: $deviceAddress")
                    
                    // 연결 상태 업데이트
                    isConnecting = false
                    isConnected = true
                    currentDeviceAddress = deviceAddress
                    cancelConnectionTimeout()
                    
                    // MTU 요청 (더 큰 패킷 전송을 위해)
                    val mtuSize = 512 // 최대 MTU 요청
                    bluetoothGatt?.requestMtu(mtuSize)
                    
                    // 콜백 호출
                    callback?.onConnected(gatt.device)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "GATT 연결 해제: $deviceAddress")
                    
                    // 연결 상태 업데이트
                    val wasConnected = isConnected
                    isConnecting = false
                    isConnected = false
                    
                    if (currentDeviceAddress == deviceAddress) {
                        currentDeviceAddress = null
                    }
                    
                    // GATT 리소스 정리
                    gatt.close()
                    if (bluetoothGatt === gatt) {
                        bluetoothGatt = null
                    }
                    
                    // 콜백 호출
                    if (wasConnected) {
                        callback?.onDisconnected()
                    }
                }
            } else {
                // 연결 오류
                Log.e(TAG, "GATT 연결 상태 변경 오류, 상태: $status, 디바이스: $deviceAddress")
                
                // 특정 오류 코드에 대한 재시도
                if (isConnecting && connectRetryCount < MAX_RETRY_COUNT) {
                    connectRetryCount++
                    Log.i(TAG, "GATT 연결 재시도 (${connectRetryCount}/${MAX_RETRY_COUNT}): $targetDeviceAddress")
                    
                    // GATT 리소스 정리
                    gatt.close()
                    bluetoothGatt = null
                    
                    // 잠시 대기 후 재시도
                    handler.postDelayed({
                        targetDeviceAddress?.let { connect(it) }
                    }, 1000)
                } else {
                    // 최대 재시도 횟수 초과 또는 연결 중이 아닌 경우
                    isConnecting = false
                    isConnected = false
                    
                    if (currentDeviceAddress == deviceAddress) {
                        currentDeviceAddress = null
                    }
                    
                    // GATT 리소스 정리
                    gatt.close()
                    if (bluetoothGatt === gatt) {
                        bluetoothGatt = null
                    }
                    
                    // 콜백 호출
                    callback?.onConnectionFailed()
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "GATT 서비스 발견 성공")
                
                // 통화 서비스 찾기
                callService = gatt.getService(BleConstants.CALL_SERVICE_UUID)
                
                if (callService != null) {
                    Log.i(TAG, "통화 서비스 발견: ${BleConstants.CALL_SERVICE_UUID}")
                    
                    // 특성 캐싱 및 알림 활성화
                    cacheCharacteristicsAndEnableNotifications()
                    
                    // 콜백 호출
                    callback?.onServicesDiscovered()
                } else {
                    Log.e(TAG, "통화 서비스를 찾을 수 없습니다")
                    callback?.onServiceNotFound()
                }
            } else {
                Log.e(TAG, "GATT 서비스 발견 실패, 상태: $status")
                callback?.onServiceDiscoveryFailed()
            }
        }
        
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "MTU 변경 성공: $mtu")
                currentMtu = mtu
                
                // 서비스 탐색 시작
                if (!gatt.discoverServices()) {
                    Log.e(TAG, "GATT 서비스 탐색 시작 실패")
                    callback?.onServiceDiscoveryFailed()
                }
            } else {
                Log.e(TAG, "MTU 변경 실패, 상태: $status, 요청 MTU: $mtu")
                
                // 기본 MTU로 서비스 탐색 시작
                if (!gatt.discoverServices()) {
                    Log.e(TAG, "GATT 서비스 탐색 시작 실패")
                    callback?.onServiceDiscoveryFailed()
                }
            }
        }
        
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (characteristic.uuid) {
                    BleConstants.CALL_STATE_CHARACTERISTIC_UUID -> {
                        if (value.isNotEmpty()) {
                            val callState = value[0]
                            Log.i(TAG, "통화 상태 읽기 성공: $callState")
                            callback?.onCallStateRead(callState)
                        }
                    }
                    else -> {
                        Log.d(TAG, "알 수 없는 특성 읽기: ${characteristic.uuid}")
                    }
                }
            } else {
                Log.e(TAG, "특성 읽기 실패, 상태: $status, 특성: ${characteristic.uuid}")
            }
        }
        
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.uuid == BleConstants.CALL_CONTROL_CHARACTERISTIC_UUID) {
                    val value = characteristic.value
                    if (value != null && value.isNotEmpty()) {
                        val command = value[0]
                        Log.i(TAG, "통화 제어 명령 쓰기 성공: $command")
                        callback?.onCallControlWriteCompleted(command)
                    }
                }
            } else {
                Log.e(TAG, "특성 쓰기 실패, 상태: $status, 특성: ${characteristic.uuid}")
                
                if (characteristic.uuid == BleConstants.CALL_CONTROL_CHARACTERISTIC_UUID) {
                    val value = characteristic.value
                    if (value != null && value.isNotEmpty()) {
                        val command = value[0]
                        callback?.onCallControlWriteFailed(command)
                    }
                }
            }
        }
        
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            when (characteristic.uuid) {
                BleConstants.AUDIO_STREAM_SERVER_TO_CLIENT_CHARACTERISTIC_UUID -> {
                    // 서버로부터 오디오 데이터 수신
                    callback?.onAudioDataReceived(value)
                }
                BleConstants.CALL_CONTROL_CHARACTERISTIC_UUID -> {
                    // 서버로부터 통화 제어 명령 수신
                    if (value.isNotEmpty()) {
                        val command = value[0]
                        Log.i(TAG, "통화 제어 명령 수신: $command")
                        callback?.onCallControlReceived(command)
                    }
                }
                BleConstants.CALL_STATE_CHARACTERISTIC_UUID -> {
                    // 서버로부터 통화 상태 알림 수신
                    if (value.isNotEmpty()) {
                        val callState = value[0]
                        Log.i(TAG, "통화 상태 알림 수신: $callState")
                        callback?.onCallStateChanged(callState)
                    }
                }
                else -> {
                    Log.d(TAG, "알 수 없는 특성 변경: ${characteristic.uuid}")
                }
            }
        }
        
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val characteristicUuid = descriptor.characteristic.uuid
                Log.i(TAG, "디스크립터 쓰기 성공: ${descriptor.uuid}, 특성: $characteristicUuid")
                
                when (characteristicUuid) {
                    BleConstants.AUDIO_STREAM_SERVER_TO_CLIENT_CHARACTERISTIC_UUID -> {
                        callback?.onAudioStreamNotificationEnabled(true)
                    }
                    BleConstants.CALL_STATE_CHARACTERISTIC_UUID -> {
                        callback?.onCallStateNotificationEnabled(true)
                    }
                    BleConstants.CALL_CONTROL_CHARACTERISTIC_UUID -> {
                        callback?.onCallControlIndicationEnabled(true)
                    }
                }
            } else {
                Log.e(TAG, "디스크립터 쓰기 실패, 상태: $status, 디스크립터: ${descriptor.uuid}")
                
                val characteristicUuid = descriptor.characteristic.uuid
                when (characteristicUuid) {
                    BleConstants.AUDIO_STREAM_SERVER_TO_CLIENT_CHARACTERISTIC_UUID -> {
                        callback?.onAudioStreamNotificationEnabled(false)
                    }
                    BleConstants.CALL_STATE_CHARACTERISTIC_UUID -> {
                        callback?.onCallStateNotificationEnabled(false)
                    }
                    BleConstants.CALL_CONTROL_CHARACTERISTIC_UUID -> {
                        callback?.onCallControlIndicationEnabled(false)
                    }
                }
            }
        }
    }
    
    /**
     * 콜백 인터페이스
     */
    interface Callback {
        fun onConnected(device: BluetoothDevice)
        fun onDisconnected()
        fun onConnectionFailed()
        fun onServicesDiscovered()
        fun onServiceNotFound()
        fun onServiceDiscoveryFailed()
        fun onAudioDataReceived(data: ByteArray)
        fun onCallControlReceived(command: Byte)
        fun onCallControlWriteCompleted(command: Byte)
        fun onCallControlWriteFailed(command: Byte)
        fun onCallStateChanged(callState: Byte)
        fun onCallStateRead(callState: Byte)
        fun onAudioStreamNotificationEnabled(success: Boolean)
        fun onCallStateNotificationEnabled(success: Boolean)
        fun onCallControlIndicationEnabled(success: Boolean)
    }
} 