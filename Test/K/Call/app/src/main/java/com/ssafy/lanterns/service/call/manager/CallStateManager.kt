package com.ssafy.lanterns.service.call.manager

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ssafy.lanterns.service.call.CallService.CallState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

/**
 * 통화 상태 관리자
 * 통화 상태와 관련된 이벤트를 관리합니다.
 */
class CallStateManager(
    private val context: Context,
    private val serviceScope: CoroutineScope,
    private val onStateChanged: (CallState, BluetoothDevice?, String?) -> Unit
) {
    private val TAG = "LANT_CallStateManager"
    
    // 현재 통화 상태
    private val _callState = MutableLiveData<CallState>(CallState.IDLE)
    val callState: LiveData<CallState> = _callState
    
    // 현재 통화 중인 디바이스
    private var currentDevice: BluetoothDevice? = null
    private var currentDeviceName: String? = null
    
    // 통화 시작 시간
    private var callStartTime: Date? = null
    
    // 오류 상태 복구 작업
    private var errorRecoveryJob: Job? = null
    
    /**
     * 통화 상태 업데이트
     */
    fun updateCallState(newState: CallState, device: BluetoothDevice? = null, deviceName: String? = null) {
        Log.d(TAG, "통화 상태 업데이트: ${_callState.value} -> $newState")
        
        if (_callState.value == newState) return
        
        // 기존 오류 복구 작업 취소
        errorRecoveryJob?.cancel()
        
        when (newState) {
            CallState.IDLE -> {
                currentDevice = null
                currentDeviceName = null
                callStartTime = null
            }
            
            CallState.OUTGOING -> {
                currentDevice = device ?: currentDevice
                currentDeviceName = deviceName ?: currentDeviceName
                callStartTime = null
            }
            
            CallState.INCOMING -> {
                currentDevice = device ?: currentDevice
                currentDeviceName = deviceName ?: currentDeviceName
                callStartTime = null
            }
            
            CallState.CONNECTED -> {
                callStartTime = Date()
            }
            
            CallState.ERROR -> {
                // 오류 상태 후 일정 시간 후 IDLE 상태로 전환
                errorRecoveryJob = serviceScope.launch(Dispatchers.Main) {
                    delay(5000) // 5초 후
                    updateCallState(CallState.IDLE)
                }
            }
        }
        
        // 상태 업데이트
        _callState.value = newState
        
        // 콜백 호출
        onStateChanged(newState, currentDevice, currentDeviceName)
    }
    
    /**
     * 기기 정보 설정
     */
    fun setDeviceInfo(device: BluetoothDevice?, deviceName: String?) {
        currentDevice = device
        currentDeviceName = deviceName
    }
    
    /**
     * 현재 통화 기기 주소 반환
     */
    fun getCurrentDeviceAddress(): String? {
        return currentDevice?.address
    }
    
    /**
     * 현재 통화 기기 이름 반환
     */
    fun getCurrentDeviceName(): String? {
        return currentDeviceName
    }
    
    /**
     * 통화 시작 시간 반환
     */
    fun getCallStartTime(): Date? {
        return callStartTime
    }
    
    /**
     * 통화 지속 시간 계산 (밀리초)
     */
    fun getCallDuration(): Long {
        val startTime = callStartTime ?: return 0
        return Date().time - startTime.time
    }
    
    /**
     * 통화 중인지 확인
     */
    fun isInCall(): Boolean {
        return _callState.value == CallState.CONNECTED || 
               _callState.value == CallState.OUTGOING || 
               _callState.value == CallState.INCOMING
    }
    
    /**
     * 통화 가능한 상태인지 확인
     */
    fun isCallable(): Boolean {
        return _callState.value == CallState.IDLE
    }
    
    /**
     * 리소스 해제
     */
    fun release() {
        errorRecoveryJob?.cancel()
        _callState.value = CallState.IDLE
    }
} 