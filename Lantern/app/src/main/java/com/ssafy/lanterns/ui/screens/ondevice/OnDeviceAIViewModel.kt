package com.ssafy.lanterns.ui.screens.ondevice

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources // Resources import 추가
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.lanterns.R // R 클래스 import 확인 (대부분 자동으로 됨)
import com.ssafy.lanterns.service.WakeWordService
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

enum class AiState {
    IDLE,        // 초기 상태 또는 비활성화 상태
    ACTIVATING,  // 웨이크워드 감지 후 AI 활성화 중 (Porcupine 중지, STT 준비 전)
    PREPARING_STT, // STT 엔진 초기화 및 마이크 준비 중
    LISTENING,   // 사용자의 음성 명령을 듣는 중 (onReadyForSpeech 이후)
    COMMAND_RECOGNIZED, // 음성 명령이 텍스트로 변환 완료
    PROCESSING,  // 인식된 명령 처리 중
    SPEAKING,    // TTS로 답변 음성 출력 중
    ERROR        // 오류 발생 상태
}

data class OnDeviceAIState(
    val currentAiState: AiState = AiState.IDLE,
    val statusMessage: String = "" // 모든 상태 메시지를 이 하나로 통합
)

private const val TAG = "OnDeviceAIViewModel"

// 타이머 상수들은 유지
private const val WAKE_WORD_AUTO_CLOSE_DELAY_MILLIS = 7000L
private const val INTERACTION_AUTO_CLOSE_DELAY_MILLIS = 10000L
private const val SPEAKING_STATE_AUTO_CLOSE_DELAY_MILLIS = 5000L
private const val ERROR_STATE_AUTO_CLOSE_DELAY_MILLIS = 4000L


@HiltViewModel
class OnDeviceAIViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : AndroidViewModel(context as Application) {

    private val _uiState = MutableStateFlow(OnDeviceAIState())
    val uiState: StateFlow<OnDeviceAIState> = _uiState.asStateFlow()

    private var autoCloseJob: Job? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechRecognitionRetryCount = 0
    private val MAX_SPEECH_RECOGNITION_RETRIES = 3

    private var textToSpeech: TextToSpeech? = null
    private var ttsInitialized = false // TTS 초기화 상태 플래그
    private var mediaPlayer: MediaPlayer? = null

    init {
        initializeTTS()
    }

    private fun initializeTTS() {
        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = textToSpeech?.setLanguage(Locale.KOREAN)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e(TAG, "TTS: 한국어를 지원하지 않거나 데이터가 없습니다.")
                        ttsInitialized = false
                    } else {
                        Log.i(TAG, "TTS: 한국어 지원 확인됨.")
                        ttsInitialized = true
                    }
                    textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            Log.d(TAG, "TTS onStart: $utteranceId")
                            if (_uiState.value.currentAiState == AiState.SPEAKING) {
                                resetAutoCloseTimer(SPEAKING_STATE_AUTO_CLOSE_DELAY_MILLIS + 2000L)
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            Log.d(TAG, "TTS onDone: $utteranceId")
                            if (_uiState.value.currentAiState == AiState.SPEAKING) {
                                startAutoCloseTimer(500, true)
                            }
                        }

                        override fun onError(utteranceId: String?) {
                            Log.e(TAG, "TTS onError: $utteranceId")
                            if (_uiState.value.currentAiState == AiState.SPEAKING) {
                                showErrorAndPrepareToClose("죄송합니다, 답변을 말씀드리는 데 문제가 생겼어요.")
                            }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            Log.e(TAG, "TTS onError: $utteranceId, errorCode: $errorCode")
                            if (_uiState.value.currentAiState == AiState.SPEAKING) {
                                showErrorAndPrepareToClose("죄송합니다, 답변을 말씀드리는 데 문제가 생겼어요. (코드: $errorCode)")
                            }
                        }
                    })
                } else {
                    Log.e(TAG, "TTS 초기화 실패, status: $status")
                    ttsInitialized = false
                }
            }
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.i(TAG, "onReadyForSpeech - 음성 입력 준비 완료. 사용자 발화 대기 시작.")
            if (_uiState.value.currentAiState == AiState.PREPARING_STT || _uiState.value.currentAiState == AiState.ACTIVATING) {
                _uiState.value = _uiState.value.copy(
                    currentAiState = AiState.LISTENING,
                    statusMessage = "말씀해주세요!" // 좀 더 명확한 안내
                )
                // (선택사항) 여기서 짧은 "띵" 효과음 재생하여 사용자에게 알림
                // playNotificationSound(context, R.raw.stt_ready_sound) // 예시
            } else {
                Log.w(TAG, "onReadyForSpeech: 현재 AI 상태가 PREPARING_STT 또는 ACTIVATING이 아님 (${_uiState.value.currentAiState})")
                _uiState.value = _uiState.value.copy(
                    currentAiState = AiState.LISTENING,
                    statusMessage = "듣고 있습니다 (상태 강제 전환)"
                )
            }
            resetAutoCloseTimer(INTERACTION_AUTO_CLOSE_DELAY_MILLIS)
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech - 사용자 발화 시작 감지")
            if (_uiState.value.currentAiState == AiState.LISTENING) {
                _uiState.value = _uiState.value.copy(statusMessage = "듣고 있어요...")
            }
            resetAutoCloseTimer(INTERACTION_AUTO_CLOSE_DELAY_MILLIS)
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Log.v(TAG, "onRmsChanged - RMS dB: $rmsdB") // 필요시에만 활성화
        }
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech - 사용자 발화 종료 감지")
            if (_uiState.value.currentAiState == AiState.LISTENING) { // 또는 COMMAND_RECOGNIZED로 넘어가기 직전
                _uiState.value = _uiState.value.copy(statusMessage = "알아듣고 있어요...")
            }
        }

        override fun onError(error: Int) {
            val errorMessageText = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "마이크 사용에 문제가 있습니다." // 간결하게
                SpeechRecognizer.ERROR_CLIENT -> "음성 인식 서비스에 일시적인 문제가 있습니다."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 필요합니다."
                SpeechRecognizer.ERROR_NETWORK -> "네트워크 연결을 확인해주세요."
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 연결 시간이 초과되었습니다."
                SpeechRecognizer.ERROR_NO_MATCH -> "죄송합니다, 잘 못 알아들었어요." // 재시도 안내는 아래에서
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "음성 인식기가 사용 중입니다. 잠시 후 다시 시도해주세요."
                SpeechRecognizer.ERROR_SERVER -> "서버에 문제가 발생했습니다."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "말씀이 없으시네요. 다시 시도하시겠어요?" // 타임아웃 시 메시지
                else -> "알 수 없는 오류가 발생했습니다. (코드: $error)"
            }
            Log.e(TAG, "onError: $errorMessageText (code: $error)")

            val shouldRetry = (error == SpeechRecognizer.ERROR_NO_MATCH || // NO_MATCH 시 적극 재시도
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || // 타임아웃 시 재시도
                    error == SpeechRecognizer.ERROR_AUDIO ||
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) &&
                    speechRecognitionRetryCount < MAX_SPEECH_RECOGNITION_RETRIES

            if (shouldRetry) {
                speechRecognitionRetryCount++
                val retryUserMessage = when(error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "$errorMessageText 다시 한 번 말씀해주시겠어요? (${speechRecognitionRetryCount}/${MAX_SPEECH_RECOGNITION_RETRIES})"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "$errorMessageText (${speechRecognitionRetryCount}/${MAX_SPEECH_RECOGNITION_RETRIES})"
                    else -> "$errorMessageText 잠시 후 다시 시도합니다... (${speechRecognitionRetryCount}/${MAX_SPEECH_RECOGNITION_RETRIES})"
                }
                _uiState.value = _uiState.value.copy(
                    currentAiState = AiState.ERROR,
                    statusMessage = retryUserMessage
                )
                Log.d(TAG, "음성 인식 재시도 ($speechRecognitionRetryCount/$MAX_SPEECH_RECOGNITION_RETRIES) - 오류 코드: $error")
                resetAutoCloseTimer(INTERACTION_AUTO_CLOSE_DELAY_MILLIS)
                viewModelScope.launch {
                    delay(if (error == SpeechRecognizer.ERROR_NO_MATCH) 1000L else 1500L) // NO_MATCH는 좀 더 빠르게 재시도
                    if (_uiState.value.currentAiState != AiState.IDLE) {
                        _uiState.value = _uiState.value.copy(
                            currentAiState = AiState.PREPARING_STT,
                            statusMessage = "다시 음성 인식을 준비합니다..."
                        )
                        startSpeechRecognition()
                    }
                }
            } else {
                Log.w(TAG, "음성 인식 재시도 중단 또는 불가. 오류 코드: $error, 재시도 횟수: $speechRecognitionRetryCount")
                showErrorAndPrepareToClose(errorMessageText) // 최종 실패 시 에러 메시지 표시 후 닫기
            }
        }

        override fun onResults(results: Bundle?) {
            // ... (이전 답변의 onResults 로직과 유사하게 유지, 로그 강화) ...
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            Log.i(TAG, "onResults: 인식된 후보들 - $matches")
            speechRecognitionRetryCount = 0
            if (!matches.isNullOrEmpty()) {
                val recognizedText = matches[0].trim() // trim() 추가
                if (recognizedText.isNotEmpty()) {
                    Log.i(TAG, "onResults: 최종 인식된 텍스트 - \"$recognizedText\"")
                    _uiState.value = _uiState.value.copy(
                        currentAiState = AiState.COMMAND_RECOGNIZED,
                        statusMessage = "\"$recognizedText\" (으)로 알아들었어요."
                    )
                    viewModelScope.launch {
                        delay(500)
                        if (_uiState.value.currentAiState == AiState.COMMAND_RECOGNIZED) {
                            processVoiceInput(recognizedText)
                        }
                    }
                } else {
                    Log.w(TAG, "onResults: 인식된 텍스트가 비어있습니다 (공백 또는 null).")
                    showErrorAndPrepareToClose("죄송합니다, 말씀하신 내용을 이해하지 못했어요.")
                }
            } else {
                Log.w(TAG, "onResults: 결과 Bundle이 비어있거나 null입니다.")
                showErrorAndPrepareToClose("음성 인식 결과를 받지 못했습니다.")
            }
        }
        override fun onPartialResults(partialResults: Bundle?) {
            // val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            // Log.d(TAG, "onPartialResults: $matches")
            // if (!matches.isNullOrEmpty() && _uiState.value.currentAiState == AiState.LISTENING) {
            //   _uiState.value = _uiState.value.copy(statusMessage = matches[0] + "...")
            // }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            if (speechRecognizer == null) {
                try {
                    Log.d(TAG, "SpeechRecognizer.createSpeechRecognizer() 시도")
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    speechRecognizer?.setRecognitionListener(recognitionListener)
                    Log.i(TAG, "SpeechRecognizer 생성 및 리스너 설정 완료")
                } catch (e: Exception) {
                    Log.e(TAG, "SpeechRecognizer 생성 실패: ${e.message}", e)
                    speechRecognizer = null
                    showErrorAndPrepareToClose("음성 인식기를 초기화하지 못했어요.")
                }
            } else {
                Log.d(TAG, "initSpeechRecognizer: 이미 SpeechRecognizer 인스턴스가 존재합니다.")
            }
        } else {
            Log.e(TAG, "음성 인식 기능을 지원하지 않는 기기입니다.")
            showErrorAndPrepareToClose("죄송하지만, 음성 인식 기능을 사용할 수 없어요.")
        }
    }

    private fun startSpeechRecognition() {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "startSpeechRecognition: RECORD_AUDIO 권한 없음.")
            showErrorAndPrepareToClose("음성 인식을 위해 마이크 사용 권한이 필요해요.")
            return
        }

        if (speechRecognizer == null) {
            Log.w(TAG, "startSpeechRecognition: speechRecognizer가 null이므로 init 시도.")
            initSpeechRecognizer()
            if (speechRecognizer == null) {
                Log.e(TAG, "startSpeechRecognition: init 후에도 speechRecognizer가 null입니다.")
                return
            }
        }
        _uiState.value = _uiState.value.copy(
            currentAiState = AiState.PREPARING_STT,
            statusMessage = "음성 인식을 시작합니다..."
        )

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)

            // 옵션 1: 한국어 명시 (현재 적용된 상태)
            // putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN.toString()) // "ko-KR"
            // putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.KOREAN.toString())


            // 옵션 2: 지원되는 언어 목록 가져오기 (디버깅용)
            // 이 인텐트를 사용하여 어떤 언어가 지원되는지 확인할 수 있습니다.
            // 하지만 실제 음성 인식에는 사용하지 않습니다. 별도의 테스트 함수로 빼서 확인 필요.
             putExtra(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS, "ko-KR") // 결과를 받는 BroadcastReceiver 필요

            // 옵션 3: 오프라인 인식 강제 (효과가 없을 수 있음, 엔진에 따라 다름)
            // putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)

            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5) // 더 많은 후보 결과 받기 (디버깅용)

            // 기존 음성 입력 길이 및 타임아웃 설정 유지
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L) // 이전 답변에서 늘린 값 유지
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)

            // putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // 필요시 디버깅용
        }

        try {
            speechRecognizer?.startListening(intent)
            Log.i(TAG, "SpeechRecognizer.startListening() 호출 성공 (언어: ${intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)})")
        } catch (e: Exception) { // SecurityException 포함
            Log.e(TAG, "SpeechRecognizer.startListening() 실패: ${e.message}", e)
            showErrorAndPrepareToClose("음성 인식을 시작하지 못했어요. (${e.javaClass.simpleName})")
        }
    }

    private fun stopSpeechRecognition() {
        if (speechRecognizer != null) {
            try {
                speechRecognizer?.stopListening()
                Log.d(TAG, "SpeechRecognizer.stopListening() 호출됨")
            } catch (e: Exception) {
                Log.e(TAG, "SpeechRecognizer.stopListening() 중 오류: ${e.message}", e)
            }
        }
    }

    fun activateAI() {
        speechRecognitionRetryCount = 0
        Log.i(TAG, "activateAI() 호출. Porcupine 일시 중지 요청.")
        context.startService(Intent(context, WakeWordService::class.java).apply {
            action = WakeWordService.ACTION_PAUSE_PORCUPINE
        })

        _uiState.value = _uiState.value.copy(
            currentAiState = AiState.ACTIVATING, // AI 활성화 시작 상태
            statusMessage = "AI를 깨우고 있어요..." // 사용자에게 피드백
        )
        startAutoCloseTimer(WAKE_WORD_AUTO_CLOSE_DELAY_MILLIS) // 이 시간 내에 PREPARING_STT 또는 LISTENING으로 가야 함

        viewModelScope.launch {
            delay(300) // Porcupine 중지 및 마이크 전환을 위한 약간의 시간 추가 (200ms -> 300ms)
            Log.d(TAG, "activateAI: 300ms 딜레이 후 startSpeechRecognition 호출 시도")
            if (_uiState.value.currentAiState == AiState.ACTIVATING) { // 그 사이 다른 상태로 바뀌지 않았다면
                startSpeechRecognition() // 여기서 PREPARING_STT -> LISTENING 상태로 진행됨
            } else {
                Log.w(TAG, "activateAI: 300ms 딜레이 후 상태가 ACTIVATING이 아님 (${_uiState.value.currentAiState}). STT 시작 안함.")
            }
        }
        // Log.d(TAG, "activateAI() 작업 시작 완료. 현재 UI 상태: ${_uiState.value.currentAiState}") // 이 로그는 비동기 호출 전에 찍힐 수 있음
    }

    private fun startAutoCloseTimer(delayMillis: Long, forceIdle: Boolean = false) {
        autoCloseJob?.cancel()
        Log.d(TAG, "startAutoCloseTimer 호출. 지연: ${delayMillis}ms, 강제IDLE: $forceIdle, 현재상태: ${_uiState.value.currentAiState}")
        autoCloseJob = viewModelScope.launch {
            Log.d(TAG, "AutoCloseJob: ${delayMillis}ms 타이머 시작.")
            delay(delayMillis)
            Log.i(TAG, "AutoCloseJob: ${delayMillis}ms 타이머 만료. 현재 AI 상태: ${_uiState.value.currentAiState}.")
            if (_uiState.value.currentAiState != AiState.IDLE || forceIdle) {
                Log.i(TAG, "AutoCloseJob: AI 상태를 IDLE로 변경합니다 (deactivateAI 호출).")
                deactivateAI(fromTimer = true)
            } else {
                Log.d(TAG, "AutoCloseJob: AI 상태가 이미 IDLE이거나 강제 IDLE 조건이 아님. 추가 작업 없음.")
            }
        }
    }

    fun resetAutoCloseTimer(delayMillis: Long = INTERACTION_AUTO_CLOSE_DELAY_MILLIS) {
        if (_uiState.value.currentAiState != AiState.IDLE) {
            Log.d(TAG, "resetAutoCloseTimer 호출. 지연 시간: ${delayMillis}ms, 현재상태: ${_uiState.value.currentAiState}")
            startAutoCloseTimer(delayMillis, false)
        } else {
            Log.d(TAG, "resetAutoCloseTimer: AI가 IDLE 상태이므로 타이머 리셋 안 함.")
        }
    }

    fun deactivateAI(fromTimer: Boolean = false) {
        Log.i(TAG, "deactivateAI() 호출됨. fromTimer: $fromTimer. Porcupine 재개 및 리소스 정리 시작.")

        context.startService(Intent(context, WakeWordService::class.java).apply {
            action = WakeWordService.ACTION_RESUME_PORCUPINE
        })
        Log.d(TAG, "deactivateAI: Porcupine 재개 (ACTION_RESUME_PORCUPINE) 인텐트 전송 완료.")

        autoCloseJob?.cancel()
        autoCloseJob = null
        Log.d(TAG, "deactivateAI: autoCloseJob 취소 완료.")

        if (speechRecognizer != null) {
            stopSpeechRecognition()
            try {
                speechRecognizer?.destroy()
                Log.d(TAG, "deactivateAI: SpeechRecognizer.destroy() 호출 완료.")
            } catch (e: Exception) {
                Log.e(TAG, "deactivateAI: SpeechRecognizer.destroy() 중 오류: ${e.message}", e)
            }
            speechRecognizer = null
            Log.d(TAG, "deactivateAI: speechRecognizer 참조 null로 설정 완료.")
        } else {
            Log.d(TAG, "deactivateAI: speechRecognizer가 이미 null이므로 destroy 호출 안 함.")
        }

        if (ttsInitialized && textToSpeech != null) {
            try {
                textToSpeech?.stop()
                Log.d(TAG, "deactivateAI: TextToSpeech.stop() 호출 완료.")
            } catch (e: Exception) {
                Log.e(TAG, "deactivateAI: TextToSpeech.stop() 중 오류: ${e.message}", e)
            }
        } else {
            Log.d(TAG, "deactivateAI: TTS가 초기화되지 않았거나 null이므로 stop 호출 안 함.")
        }

        if (mediaPlayer != null) {
            try {
                if (mediaPlayer!!.isPlaying) {
                    mediaPlayer?.stop()
                }
                mediaPlayer?.release()
                Log.d(TAG, "deactivateAI: MediaPlayer 해제 완료.")
            } catch (e: Exception) {
                Log.e(TAG, "deactivateAI: MediaPlayer 해제 중 오류: ${e.message}", e)
            }
            mediaPlayer = null
        }

        if (_uiState.value.currentAiState != AiState.IDLE) {
            _uiState.value = OnDeviceAIState(currentAiState = AiState.IDLE, statusMessage = "AI 비활성화됨")
            Log.i(TAG, "deactivateAI: UI 상태를 IDLE로 변경 완료.")
        } else {
            Log.d(TAG, "deactivateAI: UI 상태가 이미 IDLE입니다.")
        }
    }

    fun processVoiceInput(text: String) {
        Log.i(TAG, "processVoiceInput: \"$text\"")
        resetAutoCloseTimer(INTERACTION_AUTO_CLOSE_DELAY_MILLIS)

        _uiState.value = _uiState.value.copy(
            currentAiState = AiState.PROCESSING,
            statusMessage = "알겠습니다. \"$text\" 명령을 처리할게요."
        )

        viewModelScope.launch {
            delay(1500)
            if (_uiState.value.currentAiState != AiState.PROCESSING) {
                Log.w(TAG, "processVoiceInput: 처리 중 상태가 아님 (${_uiState.value.currentAiState}). 응답 생성 중단.")
                return@launch
            }

            val response = when {
                text.contains("119") && (text.contains("전화") || text.contains("연결") || text.contains("구조")) -> {
                    if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        try {
                            val intent = Intent(Intent.ACTION_CALL).apply {
                                data = android.net.Uri.parse("tel:114")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                            Log.i(TAG, "119 전화 인텐트 실행 성공")
                            "네, 바로 119에 연결할게요!"
                        } catch (e: SecurityException) {
                            Log.e(TAG, "119 전화 실행 실패 (SecurityException): ${e.message}", e)
                            "119에 연결하지 못했습니다. 전화 기능을 확인해주세요."
                        } catch (e: Exception) {
                            Log.e(TAG, "119 전화 실행 실패 (Exception): ${e.message}", e)
                            "119 연결 중 오류가 발생했어요."
                        }
                    } else {
                        Log.w(TAG, "119 전화 시도: CALL_PHONE 권한 없음")
                        "119에 전화하려면 전화 권한이 필요해요. 설정에서 허용해주세요."
                    }
                }
                listOf("도움 요청", "도움 좀 줘", "도와줘", "살려줘", "구해줘", "도움이 필요해", "도움 요청해줘").any { text.contains(it) }
                    -> {
                    try {
                        mediaPlayer?.release()
                        mediaPlayer = null

                        val soundResId = R.raw.sound_3 // 직접 참조
                        // MediaPlayer.create는 리소스 ID가 0이거나 유효하지 않으면 null을 반환하거나 예외를 발생시킬 수 있음.
                        if (soundResId != 0) { // 일반적으로 리소스 ID는 0이 아님. 안전장치로 추가.
                            mediaPlayer = MediaPlayer.create(context, soundResId)
                            if (mediaPlayer == null) { // create 실패 체크
                                Log.e(TAG, "MediaPlayer.create 실패 (sound_1 리소스: $soundResId). 파일 존재 및 형식 확인 필요.")
                                "비상벨 소리를 재생할 수 없어요. 오디오 파일을 확인해주세요."
                            } else {
                                mediaPlayer?.setOnCompletionListener { mp ->
                                    Log.d(TAG, "비상벨 재생 완료.")
                                    mp.release() // 리스너 내에서 해제
                                    mediaPlayer = null // 참조 제거
                                }
                                mediaPlayer?.start()
                                Log.i(TAG, "비상벨 재생 시작.")
                                "주변에 요청합니다."
                            }
                        } else {
                            Log.e(TAG, "비상벨 재생 실패: R.raw.sound_1 리소스 ID가 유효하지 않습니다 (0).")
                            "비상벨 소리 파일이 없거나 잘못되었어요."
                        }
                    } catch (e: Resources.NotFoundException) {
                        Log.e(TAG, "비상벨 재생 실패 (Resources.NotFoundException): ${e.message}", e)
                        "비상벨 소리 파일을 찾을 수 없어요."
                    } catch (e: Exception) {
                        Log.e(TAG, "비상벨 재생 중 일반 오류: ${e.message}", e)
                        "비상벨을 재생하는 데 문제가 생겼어요."
                    }
                }
                text.contains("안녕") -> "안녕하세요! 무엇을 도와드릴까요?"
                text.contains("이름") || text.contains("누구") -> "저는 당신의 안전을 돕는 랜턴 AI입니다. 잘 부탁드려요."
                text.contains("날씨") -> "죄송하지만, 현재 날씨 정보를 알려드릴 수는 없어요. 다른 도움이 필요하시면 말씀해주세요."
                text.contains("시간") -> "지금은 ${java.text.SimpleDateFormat("오후 h시 m분", Locale.KOREAN).format(java.util.Date())} 입니다."
                text.contains("고마워") || text.contains("감사") -> "천만에요! 언제든지 다시 불러주세요."
                text.contains("잘가") || text.contains("종료") -> {
                    deactivateAI()
                    "네, 안녕히 가세요."
                }
                text.contains("도움") || text.contains("뭐 할 수 있어") -> "저는 음성 명령으로 전화를 걸거나 비상벨을 울릴 수 있어요. 예를 들어 '119 전화해줘' 또는 '비상벨 울려줘'라고 말씀해보세요."
                else -> "\"$text\"라고 말씀하셨네요. 제가 이해할 수 있는 다른 명령을 내려주시겠어요? 예를 들어 '119 전화' 또는 '비상벨'이라고 해보세요."
            }
            showResponse(response)
        }
    }

    private fun showResponse(response: String) {
        Log.i(TAG, "showResponse: \"$response\"")
        if (_uiState.value.currentAiState == AiState.IDLE) {
            Log.w(TAG, "showResponse: AI가 이미 비활성화 상태이므로 TTS 응답을 건너<0xEB><0x9B><0x84>니다.")
            return
        }

        _uiState.value = _uiState.value.copy(
            currentAiState = AiState.SPEAKING,
            statusMessage = response
        )

        if (ttsInitialized && textToSpeech != null) {
            val result = textToSpeech?.speak(response, TextToSpeech.QUEUE_FLUSH, null, "utteranceId_${System.currentTimeMillis()}")
            if (result == TextToSpeech.ERROR) {
                Log.e(TAG, "TextToSpeech.speak() 호출 실패.")
                showErrorAndPrepareToClose("죄송합니다, 답변을 말씀드리는 데 실패했어요.")
            }
        } else {
            Log.e(TAG, "TTS가 준비되지 않아 응답을 말할 수 없습니다.")
            startAutoCloseTimer(1000, true)
        }
    }

    fun showErrorAndPrepareToClose(message: String) {
        Log.e(TAG, "showErrorAndPrepareToClose: $message")
        if (_uiState.value.currentAiState == AiState.IDLE) {
            Log.w(TAG, "showErrorAndPrepareToClose: 이미 IDLE 상태이므로 중복 호출 방지.")
            return
        }
        _uiState.value = _uiState.value.copy(
            currentAiState = AiState.ERROR,
            statusMessage = message
        )
        startAutoCloseTimer(ERROR_STATE_AUTO_CLOSE_DELAY_MILLIS, true)
    }

    override fun onCleared() {
        super.onCleared()
        Log.i(TAG, "onCleared() 호출됨. 모든 리소스 최종 해제.")
        if (_uiState.value.currentAiState != AiState.IDLE) {
            deactivateAI(fromTimer = true)
        }

        textToSpeech?.let {
            if (ttsInitialized) {
                try {
                    it.stop()
                    it.shutdown()
                    Log.d(TAG, "onCleared: TextToSpeech shutdown 완료.")
                } catch (e: Exception) {
                    Log.e(TAG, "onCleared: TextToSpeech shutdown 중 오류: ${e.message}", e)
                }
            }
            textToSpeech = null
            ttsInitialized = false
        }

        mediaPlayer?.release()
        mediaPlayer = null
        Log.d(TAG, "onCleared: MediaPlayer 최종 해제 완료.")
    }
}