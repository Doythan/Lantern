package com.ssafy.lanterns.service.call

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.ssafy.lanterns.service.call.manager.BleCallManager
import com.ssafy.lanterns.service.call.manager.CallAudioManager
import com.ssafy.lanterns.service.call.manager.CallNotificationManager
import com.ssafy.lanterns.service.call.manager.CallStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

/**
 * 통화 서비스
 * 백그라운드에서 실행되며 BLE 통화 기능을 관리합니다.
 * 알림을 통해 통화 상태를 사용자에게 표시합니다.
 */
class CallService : Service() {
    private val TAG = "LANT_CallService"
    
    // 인텐트 액션 정의
    companion object {
        const val ACTION_START_CALL = "com.ssafy.lanterns.service.call.ACTION_START_CALL"
        const val ACTION_END_CALL = "com.ssafy.lanterns.service.call.ACTION_END_CALL"
        const val ACTION_ACCEPT_CALL = "com.ssafy.lanterns.service.call.ACTION_ACCEPT_CALL"
        const val ACTION_REJECT_CALL = "com.ssafy.lanterns.service.call.ACTION_REJECT_CALL"
        
        // 인텐트 엑스트라 정의
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        const val EXTRA_DEVICE_NAME = "device_name"
    }
    
    // 바인더
    private val binder = LocalBinder()
    
    // 통화 상태
    enum class CallState {
        IDLE,       // 통화 없음
        OUTGOING,   // 발신 중
        INCOMING,   // 수신 중
        CONNECTED,  // 통화 연결됨
        ERROR       // 오류 상태
    }
    
    // 코루틴 스코프
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    
    // 매니저 클래스들
    private lateinit var notificationManager: CallNotificationManager
    private lateinit var audioManager: CallAudioManager
    private lateinit var bleCallManager: BleCallManager
    private lateinit var stateManager: CallStateManager
    
    // 발언 턴 상태
    private var isMyTurn = false
    private var opponentIsSpeaking = false
    
    /**
     * 콜백 인터페이스 정의
     */
    interface CallCallback {
        fun onCallStateChanged(callState: CallState, deviceAddress: String?, deviceName: String?)
        fun onCallError(errorMessage: String)
        fun onAudioDataReceived(data: ByteArray)
        fun onTurnChanged(isMyTurn: Boolean, opponentIsSpeaking: Boolean)
    }
    
    // 콜백 객체
    private var callback: CallCallback? = null
    
    /**
     * 콜백 설정
     */
    fun setCallCallback(callback: CallCallback?) {
        this.callback = callback
    }
    
    /**
     * 바인더 클래스
     */
    inner class LocalBinder : Binder() {
        fun getService(): CallService = this@CallService
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "CallService onCreate")
        
        // 매니저 클래스 초기화
        initializeManagers()
    }
    
    /**
     * 매니저 클래스 초기화
     */
    private fun initializeManagers() {
        // 상태 관리자 초기화
        stateManager = CallStateManager(
            context = this,
            serviceScope = serviceScope,
            onStateChanged = { newState, device, deviceName -> 
                handleCallStateChanged(newState, device, deviceName)
            }
        )
        
        // 알림 관리자 초기화
        notificationManager = CallNotificationManager(this)
        notificationManager.createNotificationChannel()
        
        // 오디오 관리자 초기화
        audioManager = CallAudioManager(this)
        
        // BLE 통화 관리자 초기화
        bleCallManager = BleCallManager(
            context = this,
            serviceScope = serviceScope,
            onCallStateChanged = { newState, device, deviceName -> 
                stateManager.updateCallState(newState, device, deviceName)
            },
            onCallError = { errorMessage ->
                callback?.onCallError(errorMessage)
            },
            onAudioDataReceived = { audioData ->
                audioManager.playAudioData(audioData)
                callback?.onAudioDataReceived(audioData)
            }
        )
        
        // 오디오 데이터 캡처 콜백 설정
        audioManager.onAudioDataCaptured = { audioData ->
            bleCallManager.sendAudioData(audioData)
        }
        
        // BLE 초기화
        bleCallManager.initialize()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: ${intent?.action}")
        
        intent?.let {
            when (it.action) {
                ACTION_START_CALL -> {
                    val deviceAddress = it.getStringExtra(EXTRA_DEVICE_ADDRESS)
                    val deviceName = it.getStringExtra(EXTRA_DEVICE_NAME)
                    
                    if (deviceAddress != null) {
                        startForeground() // 포그라운드 서비스 시작
                        initiateCall(deviceAddress, deviceName ?: "알 수 없음")
                    }
                }
                
                ACTION_END_CALL -> {
                    endCall()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                
                ACTION_ACCEPT_CALL -> {
                    acceptCall()
                    updateCallNotification()
                }
                
                ACTION_REJECT_CALL -> {
                    rejectCall()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        
        return START_NOT_STICKY
    }
    
    /**
     * 포그라운드 서비스 시작
     */
    private fun startForeground() {
        val currentState = stateManager.callState.value ?: CallState.IDLE
        val deviceName = stateManager.getCurrentDeviceName()
        
        val notification = notificationManager.createCallNotification(
            currentState, 
            deviceName
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                CallNotificationManager.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(CallNotificationManager.NOTIFICATION_ID, notification)
        }
    }
    
    /**
     * 통화 상태 변경 처리
     */
    private fun handleCallStateChanged(newState: CallState, device: BluetoothDevice?, deviceName: String?) {
        // 알림 및 오디오 상태 업데이트
        notificationManager.onCallStateChanged(newState)
        updateCallNotification()
        
        // 상태 변경 콜백 호출
        callback?.onCallStateChanged(
            newState,
            device?.address,
            deviceName
        )
        
        // 오디오 관리
        if (newState == CallState.CONNECTED) {
            // 발신 통화는 초기에 발언권을 가짐
            updateTurnState(true, false) 
        } else {
            // 통화 중이 아니면 오디오 처리 중지
            audioManager.stopRecording()
            audioManager.stopPlayback()
        }
    }
    
    /**
     * 턴 상태 업데이트
     */
    private fun updateTurnState(isMyTurnNow: Boolean, isOpponentSpeakingNow: Boolean) {
        isMyTurn = isMyTurnNow
        opponentIsSpeaking = isOpponentSpeakingNow
        
        // 오디오 상태 업데이트
        audioManager.updateAudioState(isMyTurn)
        
        // 콜백 호출
        callback?.onTurnChanged(isMyTurn, opponentIsSpeaking)
    }
    
    /**
     * 알림 업데이트
     */
    private fun updateCallNotification() {
        val currentState = stateManager.callState.value ?: CallState.IDLE
        val deviceName = stateManager.getCurrentDeviceName()
        
        notificationManager.updateCallNotification(
            currentState, 
            deviceName,
            stateManager.getCallDuration()
        )
    }
    
    /**
     * 발신 통화 시작
     */
    fun initiateCall(deviceAddress: String, deviceName: String = "알 수 없음") {
        Log.i(TAG, "통화 시작 요청: $deviceAddress ($deviceName)")
        
        // BLE 통화 매니저를 통해 통화 시작
        bleCallManager.initiateCall(deviceAddress, deviceName)
    }
    
    /**
     * 수신 통화 수락
     */
    fun acceptCall() {
        Log.i(TAG, "통화 수락")
        
        // BLE 통화 매니저를 통해 통화 수락
        if (bleCallManager.acceptCall()) {
            // 수신자는 초기에 발언권이 없음
            updateTurnState(false, false)
        }
    }
    
    /**
     * 수신 통화 거절
     */
    fun rejectCall() {
        Log.i(TAG, "통화 거절")
        
        // BLE 통화 매니저를 통해 통화 거절
        bleCallManager.rejectCall()
    }
    
    /**
     * 통화 종료
     */
    fun endCall() {
        Log.i(TAG, "통화 종료")
        
        // BLE 통화 매니저를 통해 통화 종료
        bleCallManager.endCall()
    }
    
    /**
     * 발언 시작 요청
     */
    fun requestVoiceTurn() {
        Log.i(TAG, "발언권 요청")
        
        val currentState = stateManager.callState.value ?: CallState.IDLE
        
        if (isMyTurn || currentState != CallState.CONNECTED) {
            return
        }
        
        // 발언권 요청
        if (bleCallManager.requestVoiceTurn()) {
            // 턴 상태 업데이트는 실제 응답을 받은 후에 할 예정
        }
    }
    
    /**
     * 발언 종료
     */
    fun endVoiceTurn() {
        Log.i(TAG, "발언 턴 종료")
        
        val currentState = stateManager.callState.value ?: CallState.IDLE
        
        if (!isMyTurn || currentState != CallState.CONNECTED) {
            return
        }
        
        // 발언 종료
        if (bleCallManager.endVoiceTurn()) {
            updateTurnState(false, false)
        }
    }
    
    /**
     * 통화 중인지 확인
     */
    fun isInCall(): Boolean {
        return stateManager.isInCall()
    }
    
    /**
     * 통화 가능한 상태인지 확인
     */
    fun isCallable(): Boolean {
        return stateManager.isCallable()
    }
    
    /**
     * 현재 통화 기기 주소 반환
     */
    fun getCurrentDeviceAddress(): String? {
        return stateManager.getCurrentDeviceAddress()
    }
    
    /**
     * 현재 통화 기기 이름 반환
     */
    fun getCurrentDeviceName(): String? {
        return stateManager.getCurrentDeviceName()
    }
    
    /**
     * 통화 지속 시간 반환 (밀리초)
     */
    fun getCallDuration(): Long {
        return stateManager.getCallDuration()
    }
    
    /**
     * 내 발언 턴인지 확인
     */
    fun isMyTurn(): Boolean {
        return isMyTurn
    }
    
    /**
     * 상대방이 말하고 있는지 확인
     */
    fun isOpponentSpeaking(): Boolean {
        return opponentIsSpeaking
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "CallService onDestroy")
        
        // 리소스 해제
        audioManager.release()
        bleCallManager.release()
        stateManager.release()
        
        // 코루틴 취소
        serviceScope.cancel()
    }
}