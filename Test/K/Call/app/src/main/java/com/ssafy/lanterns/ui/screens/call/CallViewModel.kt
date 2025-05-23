package com.ssafy.lanterns.ui.screens.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.lanterns.data.model.CallHistory
import com.ssafy.lanterns.data.repository.CallHistoryRepository
import com.ssafy.lanterns.service.call.CallService
import com.ssafy.lanterns.service.call.CallServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

/**
 * 통화 기능을 위한 ViewModel
 * CallServiceManager를 통해 통화 서비스와 연동하고 UI 상태를 관리합니다.
 */
@HiltViewModel
class CallViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callServiceManager: CallServiceManager,
    private val callHistoryRepository: CallHistoryRepository
) : ViewModel() {
    private val TAG = "LANT_CallViewModel"
    
    // 상태 관리
    private val _uiState = MutableStateFlow<CallUiState>(CallUiState.Idle)
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()
    
    // 통화 시작 시간
    private var callStartTime: Long = 0L
    
    // 오디오 설정
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()
    
    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()
    
    // 통화 기록
    private val _callHistories = MutableStateFlow<List<CallHistory>>(emptyList())
    val callHistories: StateFlow<List<CallHistory>> = _callHistories.asStateFlow()
    
    // 권한 상태
    private val _hasAudioPermission = MutableStateFlow(false)
    val hasAudioPermission: StateFlow<Boolean> = _hasAudioPermission.asStateFlow()
    
    init {
        checkAudioPermission()
        loadCallHistories()
    }
    
    /**
     * 오디오 권한 확인
     */
    private fun checkAudioPermission() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        _hasAudioPermission.value = hasPermission
    }
    
    /**
     * 통화 기록 로드
     */
    fun loadCallHistories() {
        viewModelScope.launch {
            callHistoryRepository.getAllCallHistories().collect { histories ->
                _callHistories.value = histories
            }
        }
    }
    
    /**
     * ViewModel 초기화
     */
    fun initialize() {
        Log.i(TAG, "CallViewModel 초기화")
        
        // 통화 서비스 바인드
        callServiceManager.bindService()
        
        // 통화 콜백 설정
        callServiceManager.setCallCallback(object : CallService.CallCallback {
            override fun onCallStateChanged(callState: CallService.CallState, deviceAddress: String?, deviceName: String?) {
                Log.i(TAG, "통화 상태 변경: $callState, 주소: $deviceAddress, 이름: $deviceName")
                
                when (callState) {
                    CallService.CallState.IDLE -> updateUiState(CallUiState.Idle)
                    
                    CallService.CallState.OUTGOING -> {
                        if (deviceAddress != null) {
                            updateUiState(CallUiState.OutgoingCall(deviceAddress))
                        }
                    }
                    
                    CallService.CallState.INCOMING -> {
                        if (deviceAddress != null) {
                            updateUiState(CallUiState.IncomingCall(
                                deviceAddress = deviceAddress,
                                deviceName = deviceName ?: "알 수 없음"
                            ))
                        }
                    }
                    
                    CallService.CallState.CONNECTED -> {
                        if (deviceAddress != null) {
                            // 통화 시작 시간 기록
                            callStartTime = System.currentTimeMillis()
                            
                            updateUiState(CallUiState.OngoingCall(
                                deviceAddress = deviceAddress,
                                deviceName = deviceName ?: "알 수 없음",
                                isMyTurn = false,
                                opponentIsSpeaking = false
                            ))
                        }
                    }
                    
                    CallService.CallState.ERROR -> updateUiState(CallUiState.Error("통화 오류가 발생했습니다."))
                }
            }
            
            override fun onCallError(errorMessage: String) {
                Log.e(TAG, "통화 오류: $errorMessage")
                updateUiState(CallUiState.Error(errorMessage))
                
                // 2초 후 Idle 상태로 돌아감
                viewModelScope.launch {
                    kotlinx.coroutines.delay(2000)
                    updateUiState(CallUiState.Idle)
                }
            }
            
            override fun onAudioDataReceived(data: ByteArray) {
                // 오디오 데이터 처리는 서비스 내부에서 완료
            }
            
            override fun onTurnChanged(isMyTurn: Boolean, opponentIsSpeaking: Boolean) {
                Log.i(TAG, "턴 상태 변경: 내 턴=$isMyTurn, 상대방 말하는 중=$opponentIsSpeaking")
                
                if (_uiState.value is CallUiState.OngoingCall) {
                    val currentState = _uiState.value as CallUiState.OngoingCall
                    updateUiState(currentState.copy(
                        isMyTurn = isMyTurn,
                        opponentIsSpeaking = opponentIsSpeaking
                    ))
                }
            }
        })
    }
    
    /**
     * 리소스 해제
     */
    override fun onCleared() {
        super.onCleared()
        endCall()
        callServiceManager.setCallCallback(null)
        callServiceManager.unbindService()
        Log.i(TAG, "CallViewModel 리소스 해제 완료")
    }
    
    /**
     * 발신 통화 시작
     */
    fun initiateCall(deviceAddress: String, deviceName: String? = null) {
        Log.i(TAG, "통화 시작 요청: $deviceAddress")
        
        if (!_hasAudioPermission.value) {
            updateUiState(CallUiState.Error("마이크 권한이 필요합니다."))
            return
        }
        
        // 이미 통화 중인 경우
        if (_uiState.value !is CallUiState.Idle) {
            Log.i(TAG, "이미 통화 중입니다. 현재 상태: ${_uiState.value}")
            return
        }
        
        // 상태 변경 (UI 즉시 반응을 위해)
        updateUiState(CallUiState.OutgoingCall(deviceAddress))
        
        // 통화 시작
        callServiceManager.startCall(deviceAddress, deviceName)
    }
    
    /**
     * 수신 통화 수락
     */
    fun acceptCall() {
        Log.i(TAG, "통화 수락")
        
        if (!_hasAudioPermission.value) {
            updateUiState(CallUiState.Error("마이크 권한이 필요합니다."))
            return
        }
        
        if (_uiState.value !is CallUiState.IncomingCall) {
            Log.e(TAG, "잘못된 상태에서 통화 수락 시도: ${_uiState.value}")
            return
        }
        
        // 통화 수락
        callServiceManager.acceptCall()
    }
    
    /**
     * 수신 통화 거절
     */
    fun rejectCall() {
        Log.i(TAG, "통화 거절")
        
        if (_uiState.value !is CallUiState.IncomingCall) {
            Log.e(TAG, "잘못된 상태에서 통화 거절 시도: ${_uiState.value}")
            return
        }
        
        // 통화 거절
        callServiceManager.rejectCall()
        
        // 거절된 통화 기록 저장
        saveCallHistory(false)
    }
    
    /**
     * 통화 종료
     */
    fun endCall() {
        Log.i(TAG, "통화 종료")
        
        // 통화가 연결된 경우만 기록 저장
        if (_uiState.value is CallUiState.OngoingCall) {
            saveCallHistory(true)
        }
        
        // 통화 종료
        callServiceManager.endCall()
        
        // 오디오 설정 초기화
        _isMuted.value = false
        _isSpeakerOn.value = false
    }
    
    /**
     * 발언 시작 요청
     */
    fun requestVoiceTurn() {
        Log.i(TAG, "발언 턴 요청")
        
        if (_uiState.value !is CallUiState.OngoingCall) {
            Log.e(TAG, "통화 중이 아닌 상태에서 발언 턴 요청: ${_uiState.value}")
            return
        }
        
        callServiceManager.requestVoiceTurn()
    }
    
    /**
     * 발언 종료
     */
    fun endVoiceTurn() {
        Log.i(TAG, "발언 턴 종료")
        
        if (_uiState.value !is CallUiState.OngoingCall) {
            Log.e(TAG, "통화 중이 아닌 상태에서 발언 턴 종료: ${_uiState.value}")
            return
        }
        
        callServiceManager.endVoiceTurn()
    }
    
    /**
     * 음소거 토글
     */
    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        
        // TODO: 실제 오디오 녹음 음소거 처리
        // 여기에 오디오 녹음 음소거 처리 코드 추가
    }
    
    /**
     * 스피커 토글
     */
    fun toggleSpeaker() {
        _isSpeakerOn.value = !_isSpeakerOn.value
        
        // TODO: 실제 오디오 출력 스피커 설정
        // 여기에 오디오 출력 스피커 설정 코드 추가
    }
    
    /**
     * 통화 기록 저장
     */
    private fun saveCallHistory(isAnswered: Boolean) {
        val currentState = _uiState.value
        var deviceAddress: String? = null
        var deviceName: String? = null
        
        when (currentState) {
            is CallUiState.OngoingCall -> {
                deviceAddress = currentState.deviceAddress
                deviceName = currentState.deviceName
            }
            
            is CallUiState.IncomingCall -> {
                deviceAddress = currentState.deviceAddress
                deviceName = currentState.deviceName
            }
            
            is CallUiState.OutgoingCall -> {
                deviceAddress = currentState.deviceAddress
                deviceName = callServiceManager.getCurrentDeviceName() ?: "알 수 없음"
            }
            
            else -> return
        }
        
        if (deviceAddress != null) {
            val duration = if (callStartTime > 0) {
                (System.currentTimeMillis() - callStartTime) / 1000 // 초 단위로 변환
            } else 0
            
            val isOutgoing = currentState is CallUiState.OutgoingCall
            
            val callHistory = CallHistory(
                id = 0, // 자동 생성
                deviceAddress = deviceAddress,
                deviceName = deviceName ?: "알 수 없음",
                timestamp = Date(),
                duration = duration.toInt(),
                isOutgoing = isOutgoing,
                isAnswered = isAnswered
            )
            
            viewModelScope.launch {
                callHistoryRepository.insertCallHistory(callHistory)
            }
        }
    }
    
    /**
     * 통화 기록 삭제
     */
    fun deleteCallHistory(historyId: Long) {
        viewModelScope.launch {
            callHistoryRepository.deleteCallHistory(historyId)
        }
    }
    
    /**
     * 모든 통화 기록 삭제
     */
    fun clearAllCallHistories() {
        viewModelScope.launch {
            callHistoryRepository.clearAllCallHistories()
        }
    }
    
    /**
     * 상태 업데이트
     */
    private fun updateUiState(newState: CallUiState) {
        viewModelScope.launch {
            _uiState.update { newState }
        }
    }
} 