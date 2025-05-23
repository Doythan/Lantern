package com.ssafy.lanterns.service.call.manager

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.ssafy.lanterns.config.BleConstants
import com.ssafy.lanterns.service.ble.gatt.CallGattClientManager
import com.ssafy.lanterns.service.ble.gatt.CallGattServerManager
import com.ssafy.lanterns.service.call.CallService
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * BLE 통화 관리 클래스
 * GATT 서버 및 클라이언트 매니저를 관리하고, 통화 제어 명령을 처리합니다.
 */
class BleCallManager(
    private val context: Context,
    private val serviceScope: CoroutineScope,
    private val onCallStateChanged: (CallService.CallState, BluetoothDevice?, String?) -> Unit,
    private val onCallError: (String) -> Unit,
    private val onAudioDataReceived: (ByteArray) -> Unit
) {
    companion object {
        private const val TAG = "LANT_BleCallManager"
    }
    
    // GATT 서버 및 클라이언트 매니저
    private val gattServerManager = CallGattServerManager.getInstance()
    private val gattClientManager = CallGattClientManager.getInstance()
    
    // 현재 통화 중인 디바이스
    private var currentCallDevice: BluetoothDevice? = null
    private var currentCallDeviceName: String? = null
    
    // 초기화 완료 여부
    private var isInitialized = false
    
    /**
     * GATT 서버 및 클라이언트 초기화
     */
    fun initialize(): Boolean {
        if (isInitialized) {
            Log.i(TAG, "이미 초기화되었습니다")
            return true
        }
        
        Log.i(TAG, "GATT 초기화 시작")
        
        // GATT 서버 초기화
        val serverInitResult = gattServerManager.initialize(context, gattServerCallback)
        if (!serverInitResult) {
            Log.e(TAG, "GATT 서버 초기화 실패")
        }
        
        // GATT 클라이언트 초기화
        val clientInitResult = gattClientManager.initialize(context, gattClientCallback)
        if (!clientInitResult) {
            Log.e(TAG, "GATT 클라이언트 초기화 실패")
        }
        
        if (serverInitResult && clientInitResult) {
            Log.i(TAG, "GATT 서버/클라이언트 초기화 성공")
            
            // GATT 서버 시작
            if (gattServerManager.startServer()) {
                Log.i(TAG, "GATT 서버 시작 성공")
                isInitialized = true
                return true
            } else {
                Log.e(TAG, "GATT 서버 시작 실패")
            }
        }
        
        return false
    }
    
    /**
     * 발신 통화 시작
     */
    fun initiateCall(deviceAddress: String, deviceName: String = "알 수 없음"): Boolean {
        Log.i(TAG, "통화 시작 요청: $deviceAddress ($deviceName)")
        
        if (!isInitialized) {
            Log.e(TAG, "초기화가 완료되지 않았습니다")
            onCallError("GATT 서비스가 초기화되지 않았습니다.")
            return false
        }
        
        // 상태 변경
        currentCallDeviceName = deviceName
        onCallStateChanged(CallService.CallState.OUTGOING, null, deviceName)
        
        // GATT 클라이언트로 연결
        if (gattClientManager.connect(deviceAddress)) {
            Log.i(TAG, "GATT 연결 시도 중...")
            return true
        } else {
            Log.e(TAG, "GATT 연결 시작 실패")
            onCallStateChanged(CallService.CallState.ERROR, null, deviceName)
            onCallError("연결 시도에 실패했습니다.")
            
            // 일정 시간 후 통화 종료
            serviceScope.launch {
                delay(3000)
                endCall()
            }
            return false
        }
    }
    
    /**
     * 수신 통화 수락
     */
    fun acceptCall(): Boolean {
        Log.i(TAG, "통화 수락")
        
        val device = currentCallDevice
        if (device == null) {
            Log.e(TAG, "현재 디바이스가 null입니다")
            onCallError("연결된 기기가 없습니다.")
            return false
        }
        
        // ACCEPT 명령 전송
        if (gattServerManager.sendCallControl(device, BleConstants.CALL_COMMAND_ACCEPT)) {
            Log.i(TAG, "수락 명령 전송 성공")
            
            // 통화 상태 변경
            gattServerManager.notifyCallState(BleConstants.CALL_STATE_CONNECTED)
            
            // 상태 변경
            onCallStateChanged(CallService.CallState.CONNECTED, device, currentCallDeviceName)
            
            return true
        } else {
            Log.e(TAG, "수락 명령 전송 실패")
            onCallError("수락 신호 전송에 실패했습니다.")
            return false
        }
    }
    
    /**
     * 수신 통화 거절
     */
    fun rejectCall(): Boolean {
        Log.i(TAG, "통화 거절")
        
        val device = currentCallDevice
        if (device == null) {
            Log.e(TAG, "현재 디바이스가 null입니다")
            onCallStateChanged(CallService.CallState.IDLE, null, null)
            return false
        }
        
        // REJECT 명령 전송
        val success = gattServerManager.sendCallControl(device, BleConstants.CALL_COMMAND_REJECT)
        if (success) {
            Log.i(TAG, "거절 명령 전송 성공")
        } else {
            Log.e(TAG, "거절 명령 전송 실패")
        }
        
        // 상태 초기화
        currentCallDevice = null
        currentCallDeviceName = null
        onCallStateChanged(CallService.CallState.IDLE, null, null)
        
        return success
    }
    
    /**
     * 통화 종료
     */
    fun endCall() {
        Log.i(TAG, "통화 종료")
        
        // 서버 측 종료 처리
        val device = currentCallDevice
        if (device != null) {
            gattServerManager.sendCallControl(device, BleConstants.CALL_COMMAND_END)
            gattServerManager.notifyCallState(BleConstants.CALL_STATE_IDLE)
        }
        
        // 클라이언트 측 종료 처리
        gattClientManager.sendCallControl(BleConstants.CALL_COMMAND_END)
        gattClientManager.disconnect()
        
        // 상태 초기화
        currentCallDevice = null
        currentCallDeviceName = null
        onCallStateChanged(CallService.CallState.IDLE, null, null)
    }
    
    /**
     * 발언 시작 요청
     */
    fun requestVoiceTurn(): Boolean {
        Log.i(TAG, "발언 턴 요청")
        return gattClientManager.sendCallControl(BleConstants.CALL_COMMAND_VOICE_START_REQUEST)
    }
    
    /**
     * 발언 종료
     */
    fun endVoiceTurn(): Boolean {
        Log.i(TAG, "발언 턴 종료")
        return gattClientManager.sendCallControl(BleConstants.CALL_COMMAND_VOICE_END)
    }
    
    /**
     * 오디오 데이터 전송
     */
    fun sendAudioData(audioData: ByteArray): Boolean {
        // 클라이언트로 오디오 데이터 전송
        return gattClientManager.sendAudioData(audioData)
    }
    
    /**
     * 리소스 해제
     */
    fun release() {
        Log.i(TAG, "BLE 통화 리소스 해제")
        
        // 통화 종료
        endCall()
        
        // GATT 서버 및 클라이언트 정리
        gattServerManager.stopServer()
        gattClientManager.disconnect()
        
        isInitialized = false
    }
    
    /**
     * GATT 서버 콜백
     */
    private val gattServerCallback = object : CallGattServerManager.Callback {
        override fun onDeviceConnected(device: BluetoothDevice) {
            Log.i(TAG, "디바이스가 GATT 서버에 연결됨: ${device.address}")
            
            // 발신 통화가 아닌 경우 수신 통화로 간주
            if (currentCallDevice == null) {
                currentCallDevice = device
                currentCallDeviceName = device.name ?: "알 수 없음"
                
                // RINGING 상태로 알림
                gattServerManager.notifyCallState(BleConstants.CALL_STATE_RINGING)
            }
        }
        
        override fun onDeviceDisconnected(device: BluetoothDevice) {
            Log.i(TAG, "디바이스가 GATT 서버에서 연결 해제됨: ${device.address}")
            
            // 현재 통화 중인 디바이스가 연결 해제된 경우
            if (currentCallDevice?.address == device.address) {
                // 상태 초기화
                serviceScope.launch {
                    // 오류 상태로 잠시 변경 후 종료
                    onCallStateChanged(CallService.CallState.ERROR, null, null)
                    onCallError("상대방과의 연결이 끊어졌습니다.")
                    
                    delay(2000)
                    
                    // 상태 초기화
                    currentCallDevice = null
                    currentCallDeviceName = null
                    onCallStateChanged(CallService.CallState.IDLE, null, null)
                }
            }
        }
        
        override fun onServiceAdded(serviceUuid: UUID) {
            Log.i(TAG, "GATT 서비스 추가됨: $serviceUuid")
        }
        
        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            Log.i(TAG, "MTU 변경됨: $mtu, 디바이스: ${device.address}")
        }
        
        override fun onAudioDataReceived(device: BluetoothDevice, data: ByteArray) {
            // 현재 통화 중인 상대방의 오디오 데이터만 처리
            if (currentCallDevice?.address == device.address) {
                // 콜백을 통해 오디오 데이터 전달
                onAudioDataReceived(data)
            }
        }
        
        override fun onCallControlReceived(device: BluetoothDevice, command: Byte) {
            Log.i(TAG, "통화 제어 명령 수신: $command, 디바이스: ${device.address}")
            
            when (command) {
                BleConstants.CALL_COMMAND_INVITE -> {
                    Log.i(TAG, "통화 초대 수신")
                    
                    // 이미 통화 중인 경우
                    if (currentCallDevice != null) {
                        Log.i(TAG, "이미 통화 중이라 통화 초대 거절")
                        gattServerManager.sendCallControl(device, BleConstants.CALL_COMMAND_REJECT)
                        return
                    }
                    
                    // 현재 디바이스 설정 및 UI 상태 업데이트
                    currentCallDevice = device
                    currentCallDeviceName = device.name ?: "알 수 없음"
                    
                    // 상태 변경
                    onCallStateChanged(CallService.CallState.INCOMING, device, currentCallDeviceName)
                }
                
                BleConstants.CALL_COMMAND_END -> {
                    Log.i(TAG, "통화 종료 수신")
                    
                    if (currentCallDevice?.address == device.address) {
                        // 상태 초기화
                        currentCallDevice = null
                        currentCallDeviceName = null
                        onCallStateChanged(CallService.CallState.IDLE, null, null)
                    }
                }
                
                BleConstants.CALL_COMMAND_VOICE_START_REQUEST -> {
                    Log.i(TAG, "발언권 요청 수신")
                    
                    // 발언권 허용
                    gattServerManager.sendCallControl(device, BleConstants.CALL_COMMAND_VOICE_START_GRANT)
                }
            }
        }
        
        override fun onAudioStreamNotificationEnabled(device: BluetoothDevice, enabled: Boolean) {
            Log.i(TAG, "오디오 스트림 알림 ${if (enabled) "활성화" else "비활성화"}: ${device.address}")
        }
        
        override fun onCallStateNotificationEnabled(device: BluetoothDevice, enabled: Boolean) {
            Log.i(TAG, "통화 상태 알림 ${if (enabled) "활성화" else "비활성화"}: ${device.address}")
        }
        
        override fun onCallControlIndicationEnabled(device: BluetoothDevice, enabled: Boolean) {
            Log.i(TAG, "통화 제어 Indicate ${if (enabled) "활성화" else "비활성화"}: ${device.address}")
        }
    }
    
    /**
     * GATT 클라이언트 콜백
     */
    private val gattClientCallback = object : CallGattClientManager.Callback {
        override fun onConnected(device: BluetoothDevice) {
            Log.i(TAG, "GATT 서버에 연결됨: ${device.address}")
            currentCallDevice = device
        }
        
        override fun onDisconnected() {
            Log.i(TAG, "GATT 서버에서 연결 해제됨")
            
            // 통화 중일 때 연결 끊김
            if (currentCallDevice != null) {
                serviceScope.launch {
                    onCallStateChanged(CallService.CallState.ERROR, null, null)
                    onCallError("연결이 끊어졌습니다.")
                    
                    // 잠시 후 종료
                    delay(2000)
                    
                    // 상태 초기화
                    currentCallDevice = null
                    currentCallDeviceName = null
                    onCallStateChanged(CallService.CallState.IDLE, null, null)
                }
            }
        }
        
        override fun onConnectionFailed() {
            Log.e(TAG, "GATT 연결 실패")
            
            serviceScope.launch {
                onCallStateChanged(CallService.CallState.ERROR, null, null)
                onCallError("연결에 실패했습니다.")
                
                // 잠시 후 종료
                delay(2000)
                
                // 상태 초기화
                currentCallDevice = null
                currentCallDeviceName = null
                onCallStateChanged(CallService.CallState.IDLE, null, null)
            }
        }
        
        override fun onServicesDiscovered() {
            Log.i(TAG, "GATT 서비스 발견됨")
            
            // 아직 발신 통화 상태라면, 통화 초대 명령 전송
            if (currentCallDevice != null) {
                gattClientManager.sendCallControl(BleConstants.CALL_COMMAND_INVITE)
                gattClientManager.readCallState() // 현재 상태 읽기
            }
        }
        
        override fun onServiceNotFound() {
            Log.e(TAG, "통화 서비스를 찾을 수 없음")
            
            serviceScope.launch {
                onCallStateChanged(CallService.CallState.ERROR, null, null)
                onCallError("통화 서비스를 지원하지 않는 기기입니다.")
                
                // 잠시 후 종료
                delay(2000)
                
                // 상태 초기화
                endCall()
            }
        }
        
        override fun onServiceDiscoveryFailed() {
            Log.e(TAG, "서비스 탐색 실패")
            
            serviceScope.launch {
                onCallStateChanged(CallService.CallState.ERROR, null, null)
                onCallError("서비스 탐색에 실패했습니다.")
                
                // 잠시 후 종료
                delay(2000)
                
                // 상태 초기화
                endCall()
            }
        }
        
        override fun onAudioDataReceived(data: ByteArray) {
            // 콜백을 통해 오디오 데이터 전달
            onAudioDataReceived(data)
        }
        
        override fun onCallControlReceived(command: Byte) {
            Log.i(TAG, "통화 제어 명령 수신: $command")
            
            when (command) {
                BleConstants.CALL_COMMAND_ACCEPT -> {
                    Log.i(TAG, "통화 수락 수신")
                    
                    // 발신 통화 상태에서 수락 받음
                    onCallStateChanged(CallService.CallState.CONNECTED, currentCallDevice, currentCallDeviceName)
                }
                
                BleConstants.CALL_COMMAND_REJECT -> {
                    Log.i(TAG, "통화 거절 수신")
                    
                    serviceScope.launch {
                        // 오류 상태로 변경
                        onCallStateChanged(CallService.CallState.ERROR, null, null)
                        onCallError("상대방이 통화를 거절했습니다.")
                        
                        // 잠시 후 종료
                        delay(2000)
                        
                        // 상태 초기화
                        currentCallDevice = null
                        currentCallDeviceName = null
                        onCallStateChanged(CallService.CallState.IDLE, null, null)
                    }
                }
                
                BleConstants.CALL_COMMAND_END -> {
                    Log.i(TAG, "통화 종료 수신")
                    
                    // 상태 초기화
                    currentCallDevice = null
                    currentCallDeviceName = null
                    onCallStateChanged(CallService.CallState.IDLE, null, null)
                    
                    // 연결 해제
                    gattClientManager.disconnect()
                }
                
                BleConstants.CALL_COMMAND_VOICE_START_GRANT -> {
                    Log.i(TAG, "발언권 허가 수신")
                    // 발언권 허가 받음 - 오디오 처리 필요
                }
            }
        }
        
        // 기타 필요한 콜백 메서드 구현
        override fun onCallControlWriteCompleted(command: Byte) {}
        override fun onCallControlWriteFailed(command: Byte) {}
        override fun onCallStateChanged(callState: Byte) {}
        override fun onCallStateRead(callState: Byte) {}
        override fun onAudioStreamNotificationEnabled(success: Boolean) {}
        override fun onCallStateNotificationEnabled(success: Boolean) {}
        override fun onCallControlIndicationEnabled(success: Boolean) {}
    }
} 