package com.ssafy.lanterns.service.call

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 통화 서비스 관리자
 * 통화 서비스에 대한 인터페이스를 제공합니다.
 */
@Singleton
class CallServiceManager @Inject constructor(private val context: Context) {
    private val TAG = "LANT_CallServiceManager"
    
    // 서비스 바인드 상태
    private var callService: CallService? = null
    private var isBound = false
    
    // 콜백
    private var callback: CallService.CallCallback? = null
    
    /**
     * 서비스 연결 객체
     */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CallService.LocalBinder
            callService = binder.getService()
            isBound = true
            
            // 콜백 설정
            callService?.setCallCallback(callback)
            
            Log.i(TAG, "통화 서비스에 연결됨")
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            callService = null
            isBound = false
            Log.i(TAG, "통화 서비스 연결 해제됨")
        }
    }
    
    /**
     * 콜백 설정
     */
    fun setCallCallback(callback: CallService.CallCallback?) {
        this.callback = callback
        
        // 현재 서비스가 바인드되어 있다면 콜백 설정
        callService?.setCallCallback(callback)
    }
    
    /**
     * 서비스 바인드
     */
    fun bindService() {
        if (isBound) return
        
        val intent = Intent(context, CallService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        
        Log.i(TAG, "통화 서비스 바인드 요청")
    }
    
    /**
     * 서비스 언바인드
     */
    fun unbindService() {
        if (!isBound) return
        
        callService?.setCallCallback(null)
        context.unbindService(serviceConnection)
        isBound = false
        
        Log.i(TAG, "통화 서비스 언바인드")
    }
    
    /**
     * 통화 시작
     */
    fun startCall(deviceAddress: String, deviceName: String?) {
        Log.i(TAG, "통화 시작: $deviceAddress")
        
        val intent = Intent(context, CallService::class.java).apply {
            action = CallService.ACTION_START_CALL
            putExtra(CallService.EXTRA_DEVICE_ADDRESS, deviceAddress)
            putExtra(CallService.EXTRA_DEVICE_NAME, deviceName)
        }
        
        context.startForegroundService(intent)
        
        // 서비스 바인드가 안 되어 있다면 바인드
        if (!isBound) {
            bindService()
        }
    }
    
    /**
     * 통화 수락
     */
    fun acceptCall() {
        Log.i(TAG, "통화 수락")
        
        callService?.acceptCall() ?: run {
            val intent = Intent(context, CallService::class.java).apply {
                action = CallService.ACTION_ACCEPT_CALL
            }
            context.startForegroundService(intent)
        }
    }
    
    /**
     * 통화 거절
     */
    fun rejectCall() {
        Log.i(TAG, "통화 거절")
        
        callService?.rejectCall() ?: run {
            val intent = Intent(context, CallService::class.java).apply {
                action = CallService.ACTION_REJECT_CALL
            }
            context.startForegroundService(intent)
        }
    }
    
    /**
     * 통화 종료
     */
    fun endCall() {
        Log.i(TAG, "통화 종료")
        
        callService?.endCall() ?: run {
            val intent = Intent(context, CallService::class.java).apply {
                action = CallService.ACTION_END_CALL
            }
            context.startForegroundService(intent)
        }
    }
    
    /**
     * 발언 턴 요청
     */
    fun requestVoiceTurn() {
        callService?.requestVoiceTurn()
    }
    
    /**
     * 발언 턴 종료
     */
    fun endVoiceTurn() {
        callService?.endVoiceTurn()
    }
    
    /**
     * 통화 중인지 확인
     */
    fun isInCall(): Boolean {
        return callService?.isInCall() ?: false
    }
    
    /**
     * 통화 가능한 상태인지 확인
     */
    fun isCallable(): Boolean {
        return callService?.isCallable() ?: true
    }
    
    /**
     * 현재 통화 기기 주소 반환
     */
    fun getCurrentDeviceAddress(): String? {
        return callService?.getCurrentDeviceAddress()
    }
    
    /**
     * 현재 통화 기기 이름 반환
     */
    fun getCurrentDeviceName(): String? {
        return callService?.getCurrentDeviceName()
    }
    
    /**
     * 통화 지속 시간 반환 (밀리초)
     */
    fun getCallDuration(): Long {
        return callService?.getCallDuration() ?: 0
    }
} 