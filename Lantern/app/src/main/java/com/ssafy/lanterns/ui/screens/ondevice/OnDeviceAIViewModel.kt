package com.ssafy.lanterns.ui.screens.ondevice

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * AI 상태를 나타내는 Enum
 */
enum class AiState {
    IDLE,                // 대기 중 (AI 비활성화 또는 응답 후)
    LISTENING,           // 음성 인식 중
    COMMAND_RECOGNIZED,  // 명령 인식됨
    PROCESSING,          // 음성 처리 중
    SPEAKING,            // AI 응답 중
    ERROR                // 오류 발생
}

/**
 * 온디바이스 AI 화면의 상태를 관리하는 ViewModel
 */
data class OnDeviceAIState(
    val currentAiState: AiState = AiState.IDLE,
    val listeningMessage: String = "듣는 중",
    val commandRecognizedMessage: String = "명령 인식 완료",
    val processingMessage: String = "처리 중",
    val responseMessage: String = "", // AI의 실제 응답 내용
    val errorMessage: String = "오류 발생"
)

private const val TAG = "OnDeviceAIViewModel"

@HiltViewModel
class OnDeviceAIViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : AndroidViewModel(context as Application) {
    
    // UI 상태
    private val _uiState = MutableStateFlow(OnDeviceAIState())
    val uiState: StateFlow<OnDeviceAIState> = _uiState.asStateFlow()
    
    // 자동 닫기 타이머
    private var autoCloseJob: Job? = null
    private val autoCloseDelayMillis = 5000L // 기본 5초, 사용자가 활동 없으면 닫힘
    private val speakingStateAutoCloseDelayMillis = 3000L // 응답 후 3초 뒤 IDLE로
    
    // 음성 인식기
    private var speechRecognizer: SpeechRecognizer? = null
    
    // RecognitionListener 구현
    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech")
            _uiState.value = _uiState.value.copy(
                currentAiState = AiState.LISTENING,
                listeningMessage = "말씀해주세요"
            )
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech")
            _uiState.value = _uiState.value.copy(
                listeningMessage = "듣고 있어요..."
            )
        }

        override fun onRmsChanged(rmsdB: Float) {
            // 볼륨 변경 시 호출 (선택적으로 처리)
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // 버퍼가 채워질 때 호출 (선택적으로 처리)
        }

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech")
            // 사용자 발화 종료 - 이후 결과 처리를 기다림
        }

        override fun onError(error: Int) {
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "오디오 에러가 발생했습니다"
                SpeechRecognizer.ERROR_CLIENT -> "클라이언트 에러가 발생했습니다"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "권한이 없습니다"
                SpeechRecognizer.ERROR_NETWORK -> "네트워크 에러가 발생했습니다"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 타임아웃이 발생했습니다"
                SpeechRecognizer.ERROR_NO_MATCH -> "음성을 인식하지 못했습니다. 다시 말씀해주세요"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "음성 인식기가 사용 중입니다"
                SpeechRecognizer.ERROR_SERVER -> "서버 에러가 발생했습니다"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "입력 시간이 초과되었습니다"
                else -> "알 수 없는 에러가 발생했습니다"
            }
            Log.e(TAG, "onError: $errorMessage")
            showError(errorMessage)
            
            // 일부 오류는 다시 시작 가능
            if (error == SpeechRecognizer.ERROR_NO_MATCH || 
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                viewModelScope.launch {
                    delay(1500) // 잠시 대기
                    // 음성 인식 다시 시작
                    if (_uiState.value.currentAiState != AiState.IDLE) {
                        startSpeechRecognition()
                    }
                }
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            Log.d(TAG, "onResults: $matches")
            if (!matches.isNullOrEmpty()) {
                val recognizedText = matches[0]
                if (recognizedText.isNotEmpty()) {
                    processVoiceInput(recognizedText)
                } else {
                    showError("인식된 텍스트가 없습니다")
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            // 부분 결과 처리 (선택적으로 처리)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // 추가 이벤트 처리
        }
    }
    
    // 음성 인식 시작 준비
    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(recognitionListener)
            }
        } else {
            Log.e(TAG, "음성 인식 기능을 지원하지 않는 기기입니다")
            showError("음성 인식 기능을 지원하지 않는 기기입니다")
        }
    }
    
    // 음성 인식 시작
    private fun startSpeechRecognition() {
        initSpeechRecognizer()
        
        // 마이크 권한 확인
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            showError("마이크 권한이 필요합니다")
            return
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // 무음 시간 설정 (밀리초)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
        }
        
        try {
            speechRecognizer?.startListening(intent)
            resetAutoCloseTimer()
        } catch (e: Exception) {
            Log.e(TAG, "음성 인식 시작 실패: ${e.message}")
            showError("음성 인식을 시작할 수 없습니다")
        }
    }
    
    // 음성 인식 중지
    private fun stopSpeechRecognition() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "음성 인식 중지 실패: ${e.message}")
        }
    }
    
    // AI 화면 활성화 (음성 인식 시작)
    fun activateAI() {
        _uiState.value = _uiState.value.copy(currentAiState = AiState.LISTENING)
        startAutoCloseTimer(autoCloseDelayMillis)
        startSpeechRecognition()
    }
    
    // 자동 닫기 타이머 시작
    private fun startAutoCloseTimer(delayMillis: Long) {
        autoCloseJob?.cancel()
        autoCloseJob = viewModelScope.launch {
            delay(delayMillis)
            if (_uiState.value.currentAiState != AiState.IDLE && _uiState.value.currentAiState != AiState.SPEAKING) {
                // SPEAKING 상태가 아니면 IDLE로 변경하며 모달 닫기 유도
                 _uiState.value = _uiState.value.copy(currentAiState = AiState.IDLE)
            } else if (_uiState.value.currentAiState == AiState.SPEAKING) {
                // SPEAKING 상태였다면 IDLE로 전환
                _uiState.value = _uiState.value.copy(currentAiState = AiState.IDLE)
            }
        }
    }
    
    // 타이머 재설정 (사용자 상호작용 시 호출)
    fun resetAutoCloseTimer() {
        // IDLE 상태가 아닐 때만 타이머 리셋
        if (_uiState.value.currentAiState != AiState.IDLE) {
            startAutoCloseTimer(autoCloseDelayMillis)
        }
    }
    
    // AI 화면 수동 비활성화 (예: 닫기 버튼 클릭)
    fun deactivateAI() {
        autoCloseJob?.cancel()
        autoCloseJob = null
        _uiState.value = _uiState.value.copy(currentAiState = AiState.IDLE)
        stopSpeechRecognition()
    }
    
    // 음성 인식 결과 처리 요청
    fun processVoiceInput(text: String) {
        resetAutoCloseTimer() // 사용자 입력이 있었으므로 타이머 리셋
        
        // 명령어 인식 상태로 먼저 변경
        _uiState.value = _uiState.value.copy(
            currentAiState = AiState.COMMAND_RECOGNIZED,
            commandRecognizedMessage = "인식된 텍스트: \"$text\""
        )
        
        // 3초 후 처리 상태로 변경
        viewModelScope.launch {
            delay(2000) // 2초 후 처리 시작
            _uiState.value = _uiState.value.copy(currentAiState = AiState.PROCESSING)
            
            // 실제 AI 처리를 시뮬레이션
            delay(2000) // 처리 시간 가정
            
            // 간단한 응답 생성 로직
            val response = when {
                text.contains("안녕") -> "안녕하세요! 무엇을 도와드릴까요?"
                text.contains("이름") -> "저는 랜턴 AI입니다. 반갑습니다."
                text.contains("날씨") -> "오늘은 맑은 하늘이 예상됩니다."
                text.contains("시간") -> "현재 시간은 ${java.text.SimpleDateFormat("HH:mm").format(java.util.Date())}입니다."
                text.contains("도움") -> "음성 기반 AI 서비스입니다. 간단한 질문에 답변해드릴 수 있어요."
                else -> "\"$text\"에 대한 답변을 준비 중입니다."
            }
            
            showResponse(response)
        }
    }
    
    // 성공적인 응답 표시
    fun showResponse(response: String) {
        _uiState.value = _uiState.value.copy(
            currentAiState = AiState.SPEAKING,
            responseMessage = response
        )
        // 응답 표시 후 일정 시간 뒤 IDLE 상태로 변경 (자동 닫기 타이머와 연동)
        startAutoCloseTimer(speakingStateAutoCloseDelayMillis)
    }
    
    // 오류 상태 표시
    fun showError(message: String) {
        _uiState.value = _uiState.value.copy(
            currentAiState = AiState.ERROR,
            errorMessage = message
        )
        // 오류 표시 후 일정 시간 뒤 IDLE 상태로 변경
        startAutoCloseTimer(speakingStateAutoCloseDelayMillis)
    }
    
    // ViewModel 소멸 시 타이머 정리
    override fun onCleared() {
        super.onCleared()
        autoCloseJob?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
} 