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
    val statusMessage: String = "", // 모든 상태 메시지를 이 하나로 통합
    val isEmergencyVisualsActive: Boolean = false // 긴급 상황 시각 효과 활성화 플래그
)

private const val TAG = "OnDeviceAIViewModel"

// 타이머 상수들은 유지
private const val WAKE_WORD_AUTO_CLOSE_DELAY_MILLIS = 7000L
private const val INTERACTION_AUTO_CLOSE_DELAY_MILLIS = 10000L
private const val SPEAKING_STATE_AUTO_CLOSE_DELAY_MILLIS = 5000L
private const val ERROR_STATE_AUTO_CLOSE_DELAY_MILLIS = 4000L
private const val EMERGENCY_VISUAL_DURATION_MILLIS = 8000L // 긴급 시각 효과 지속 시간


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
    private var emergencyVisualsJob: Job? = null // 긴급 시각 효과 타이머 잡


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
                                // 만약 isEmergencyVisualsActive가 true라면, TTS 시작 시 타이머를 연장하거나 TTS 길이에 맞춰 조정
                                val delay = if (_uiState.value.isEmergencyVisualsActive) {
                                    EMERGENCY_VISUAL_DURATION_MILLIS // 긴급 시에는 긴급 타이머가 우선
                                } else {
                                    SPEAKING_STATE_AUTO_CLOSE_DELAY_MILLIS + 2000L
                                }
                                resetAutoCloseTimer(delay)
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            Log.d(TAG, "TTS onDone: $utteranceId")
                            if (_uiState.value.currentAiState == AiState.SPEAKING) {
                                // 긴급 시각 효과가 활성화되어 있지 않을 때만 일반적인 자동 닫기 타이머 시작
                                if (!_uiState.value.isEmergencyVisualsActive) {
                                    startAutoCloseTimer(500, true)
                                }
                                // 긴급 시각 효과는 자체 타이머(emergencyVisualsJob)에 의해 관리됨
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
                    statusMessage = "말씀해주세요!"
                )
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
                _uiState.value = _uiState.value.copy(statusMessage = "듣고 있어요")
            }
            resetAutoCloseTimer(INTERACTION_AUTO_CLOSE_DELAY_MILLIS)
        }

        override fun onRmsChanged(rmsdB: Float) { }
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech - 사용자 발화 종료 감지")
            if (_uiState.value.currentAiState == AiState.LISTENING) {
                _uiState.value = _uiState.value.copy(statusMessage = "알아듣고 있어요")
            }
        }

        override fun onError(error: Int) {
            val errorMessageText = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "마이크 사용에 문제가 있습니다."
                SpeechRecognizer.ERROR_CLIENT -> "음성 인식 서비스에 일시적인 문제가 있습니다."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 필요합니다."
                SpeechRecognizer.ERROR_NETWORK -> "네트워크 연결을 확인해주세요."
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 연결 시간이 초과되었습니다."
                SpeechRecognizer.ERROR_NO_MATCH -> "죄송합니다, 잘 못 알아들었어요."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "음성 인식기가 사용 중입니다. 잠시 후 다시 시도해주세요."
                SpeechRecognizer.ERROR_SERVER -> "서버에 문제가 발생했습니다."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "말씀이 없으시네요. 다시 시도하시겠어요?"
                else -> "알 수 없는 오류가 발생했습니다. (코드: $error)"
            }
            Log.e(TAG, "onError: $errorMessageText (code: $error)")

            // 긴급 시각 효과가 활성화 중일 때는 재시도 로직을 다르게 가져갈 수 있음 (예: 즉시 종료 또는 다른 메시지)
            // 여기서는 일단 기존 로직 유지
            val shouldRetry = (error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                    error == SpeechRecognizer.ERROR_AUDIO ||
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) &&
                    speechRecognitionRetryCount < MAX_SPEECH_RECOGNITION_RETRIES &&
                    !_uiState.value.isEmergencyVisualsActive // 긴급 상황 아닐때만 재시도

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
                    // isEmergencyVisualsActive는 여기서 변경하지 않음
                )
                Log.d(TAG, "음성 인식 재시도 ($speechRecognitionRetryCount/$MAX_SPEECH_RECOGNITION_RETRIES) - 오류 코드: $error")
                resetAutoCloseTimer(INTERACTION_AUTO_CLOSE_DELAY_MILLIS)
                viewModelScope.launch {
                    delay(if (error == SpeechRecognizer.ERROR_NO_MATCH) 1000L else 1500L)
                    if (_uiState.value.currentAiState != AiState.IDLE && !_uiState.value.isEmergencyVisualsActive) { // 긴급 상황 아닐때만
                        _uiState.value = _uiState.value.copy(
                            currentAiState = AiState.PREPARING_STT,
                            statusMessage = "다시 음성 인식을 준비합니다..."
                        )
                        startSpeechRecognition()
                    }
                }
            } else {
                Log.w(TAG, "음성 인식 재시도 중단 또는 불가. 오류 코드: $error, 재시도 횟수: $speechRecognitionRetryCount, 긴급상황: ${_uiState.value.isEmergencyVisualsActive}")
                showErrorAndPrepareToClose(if (_uiState.value.isEmergencyVisualsActive) "긴급 상황 중 오류가 발생했습니다." else errorMessageText)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            Log.i(TAG, "onResults: 인식된 후보들 - $matches")
            speechRecognitionRetryCount = 0
            if (!matches.isNullOrEmpty()) {
                val recognizedText = matches[0].trim()
                if (recognizedText.isNotEmpty()) {
                    Log.i(TAG, "onResults: 최종 인식된 텍스트 - \"$recognizedText\"")
                    _uiState.value = _uiState.value.copy(
                        currentAiState = AiState.COMMAND_RECOGNIZED,
                        statusMessage = "\"$recognizedText\" (으)로 알아들었어요."
                        // isEmergencyVisualsActive는 processVoiceInput에서 결정
                    )
                    viewModelScope.launch {
                        delay(500) // 잠깐 인식된 텍스트 보여주고
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
        override fun onPartialResults(partialResults: Bundle?) {}
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
            initSpeechRecognizer()
            if (speechRecognizer == null) return
        }
        _uiState.value = _uiState.value.copy(
            currentAiState = AiState.PREPARING_STT,
            statusMessage = "음성 인식을 시작합니다..."
            // isEmergencyVisualsActive는 여기서 변경하지 않음
        )

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        }

        try {
            speechRecognizer?.startListening(intent)
            Log.i(TAG, "SpeechRecognizer.startListening() 호출 성공")
        } catch (e: Exception) {
            Log.e(TAG, "SpeechRecognizer.startListening() 실패: ${e.message}", e)
            showErrorAndPrepareToClose("음성 인식을 시작하지 못했어요. (${e.javaClass.simpleName})")
        }
    }

    private fun stopSpeechRecognition() {
        speechRecognizer?.stopListening()
        Log.d(TAG, "SpeechRecognizer.stopListening() 호출됨")
    }

    fun activateAI() {
        speechRecognitionRetryCount = 0
        Log.i(TAG, "activateAI() 호출. Porcupine 일시 중지 요청.")
        context.startService(Intent(context, WakeWordService::class.java).apply {
            action = WakeWordService.ACTION_PAUSE_PORCUPINE
        })

        // 긴급 시각 효과 관련 초기화
        emergencyVisualsJob?.cancel()
        _uiState.value = _uiState.value.copy(
            currentAiState = AiState.ACTIVATING,
            statusMessage = "AI를 깨우고 있어요...",
            isEmergencyVisualsActive = false // AI 활성화 시에는 긴급 효과 초기화
        )
        startAutoCloseTimer(WAKE_WORD_AUTO_CLOSE_DELAY_MILLIS)

        viewModelScope.launch {
            delay(300)
            if (_uiState.value.currentAiState == AiState.ACTIVATING) {
                startSpeechRecognition()
            }
        }
    }

    private fun startAutoCloseTimer(delayMillis: Long, forceIdle: Boolean = false) {
        autoCloseJob?.cancel()
        Log.d(TAG, "startAutoCloseTimer 호출. 지연: ${delayMillis}ms, 강제IDLE: $forceIdle, 현재상태: ${_uiState.value.currentAiState}, 긴급모드: ${_uiState.value.isEmergencyVisualsActive}")

        // 긴급 시각 효과가 활성화되어 있고, 이 타이머가 긴급 효과 타이머보다 짧다면, 긴급 효과 타이머를 존중.
        if (_uiState.value.isEmergencyVisualsActive && emergencyVisualsJob != null && emergencyVisualsJob!!.isActive) {
            Log.d(TAG, "긴급 시각 효과 활성화 중. 일반 자동 닫기 타이머는 긴급 효과 종료 후 고려됨.")
            return // 긴급 효과가 끝나면 emergencyVisualsJob 내부에서 deactivateAI가 호출될 수 있음
        }

        autoCloseJob = viewModelScope.launch {
            delay(delayMillis)
            Log.i(TAG, "AutoCloseJob: ${delayMillis}ms 타이머 만료. 현재 AI 상태: ${_uiState.value.currentAiState}.")
            // 긴급 효과가 여전히 활성 상태면 (예: 사용자가 대화 없이 가만히 있을 때) 여기서 닫지 않음.
            // 긴급 효과는 자체 타이머(emergencyVisualsJob)로 관리됨.
            if (!_uiState.value.isEmergencyVisualsActive && (_uiState.value.currentAiState != AiState.IDLE || forceIdle)) {
                Log.i(TAG, "AutoCloseJob: AI 상태를 IDLE로 변경 (deactivateAI 호출).")
                deactivateAI(fromTimer = true)
            } else {
                Log.d(TAG, "AutoCloseJob: 추가 작업 없음 (긴급모드: ${_uiState.value.isEmergencyVisualsActive}, IDLE상태이거나 강제 IDLE조건 아님).")
            }
        }
    }

    fun resetAutoCloseTimer(delayMillis: Long = INTERACTION_AUTO_CLOSE_DELAY_MILLIS) {
        // 긴급 시각 효과가 활성화된 경우, 이 타이머는 긴급 효과 타이머에 의해 대체되거나 영향을 받음.
        if (_uiState.value.isEmergencyVisualsActive) {
            Log.d(TAG, "resetAutoCloseTimer: 긴급 시각 효과 활성화 중. 타이머는 emergencyVisualsJob에 의해 관리됨.")
            // 긴급 효과 타이머를 리셋할지 여부는 정책에 따라 결정 (여기서는 일단 리셋 안 함)
            return
        }

        if (_uiState.value.currentAiState != AiState.IDLE) {
            Log.d(TAG, "resetAutoCloseTimer 호출. 지연 시간: ${delayMillis}ms, 현재상태: ${_uiState.value.currentAiState}")
            startAutoCloseTimer(delayMillis, false)
        }
    }

    fun deactivateAI(fromTimer: Boolean = false) {
        Log.i(TAG, "deactivateAI() 호출됨. fromTimer: $fromTimer. Porcupine 재개 및 리소스 정리 시작.")

        context.startService(Intent(context, WakeWordService::class.java).apply {
            action = WakeWordService.ACTION_RESUME_PORCUPINE
        })

        autoCloseJob?.cancel()
        autoCloseJob = null
        emergencyVisualsJob?.cancel() // 긴급 시각 효과 타이머도 취소
        emergencyVisualsJob = null

        speechRecognizer?.destroy()
        speechRecognizer = null

        ttsInitialized.let {
            if(it && textToSpeech != null){
                textToSpeech?.stop()
                // TTS 엔진은 onCleared에서 shutdown
            }
        }

        mediaPlayer?.release()
        mediaPlayer = null

        // 상태를 IDLE로 변경하고 isEmergencyVisualsActive도 false로 설정
        if (_uiState.value.currentAiState != AiState.IDLE || _uiState.value.isEmergencyVisualsActive) {
            _uiState.value = OnDeviceAIState(
                currentAiState = AiState.IDLE,
                statusMessage = "AI 비활성화됨",
                isEmergencyVisualsActive = false // 확실하게 false로 초기화
            )
            Log.i(TAG, "deactivateAI: UI 상태를 IDLE로 변경 및 긴급 효과 비활성화 완료.")
        }
    }

    fun processVoiceInput(text: String) {
        Log.i(TAG, "processVoiceInput: \"$text\"")

        val isEmergencyCommand = listOf("도움 요청", "도움 좀 줘", "도와줘", "살려줘", "구해줘", "도움이 필요해", "도움 요청해줘").any { text.contains(it) }

        if (isEmergencyCommand) {
            _uiState.value = _uiState.value.copy(
                currentAiState = AiState.PROCESSING, // 또는 AiState.COMMAND_RECOGNIZED 유지
                statusMessage = "긴급 상황 감지! \"$text\"", // TTS로 나갈 메시지는 아래 response 변수에서 설정
                isEmergencyVisualsActive = true
            )
            emergencyVisualsJob?.cancel() // 이전 작업이 있다면 취소
            emergencyVisualsJob = viewModelScope.launch {
                Log.d(TAG, "긴급 시각 효과 및 사이렌 루프 시작 (${EMERGENCY_VISUAL_DURATION_MILLIS}ms).")

                // --- MediaPlayer 초기화 및 루프 재생 시작 ---
                var sirenPlaybackSuccessful = false
                try {
                    mediaPlayer?.release() // 이전 MediaPlayer가 있다면 해제
                    mediaPlayer = MediaPlayer.create(context, R.raw.sound_3)?.apply {
                        isLooping = true // 루프 재생 설정
                        setOnPreparedListener {
                            Log.d(TAG, "MediaPlayer 준비 완료, 사이렌 루프 시작.")
                            start() // 준비가 되면 재생 시작
                            sirenPlaybackSuccessful = true
                        }
                        setOnErrorListener { mp, what, extra ->
                            Log.e(TAG, "MediaPlayer 오류 발생: what($what), extra($extra)")
                            sirenPlaybackSuccessful = false
                            // 여기서 사용자에게 알림을 줄 수도 있습니다. (예: TTS 메시지 변경)
                            true // 오류를 처리했음을 반환
                        }
                    }
                    if (mediaPlayer == null) { // MediaPlayer.create 실패 시
                        Log.e(TAG, "MediaPlayer.create 실패 (sound_3). 리소스 확인 필요.")
                        sirenPlaybackSuccessful = false
                    }
                } catch (e: Resources.NotFoundException) {
                    Log.e(TAG, "사이렌 리소스 파일(sound_3)을 찾을 수 없습니다: ${e.message}", e)
                    sirenPlaybackSuccessful = false
                } catch (e: Exception) {
                    Log.e(TAG, "사이렌 재생 중 예기치 않은 오류: ${e.message}", e)
                    sirenPlaybackSuccessful = false
                }
                // --- MediaPlayer 초기화 및 루프 재생 끝 ---

                // TTS로 안내할 메시지 설정 (사이렌 재생 성공 여부에 따라 다르게 할 수 있음)
                val ttsMessageForEmergency = if (sirenPlaybackSuccessful) {
                    "주변에 긴급 상황을 알립니다!"
                } else {
                    "긴급 상황입니다! 사이렌 재생에 실패했습니다."
                }
                // TTS 호출을 위해 상태 업데이트 (만약 TTS를 여기서 바로 하고 싶다면)
                // 또는 아래 showResponse를 호출하기 전에 statusMessage를 업데이트 할 수 있습니다.
                // _uiState.value = _uiState.value.copy(statusMessage = ttsMessageForEmergency)
                // showResponse(ttsMessageForEmergency) // TTS를 여기서 바로 시작할 경우

                // UI 효과 지속 시간 동안 대기
                delay(EMERGENCY_VISUAL_DURATION_MILLIS)

                Log.i(TAG, "긴급 시각 효과 타이머 만료.")

                // --- MediaPlayer 중지 및 해제 ---
                mediaPlayer?.let {
                    try {
                        if (it.isPlaying) {
                            it.stop()
                            Log.d(TAG, "사이렌 루프 재생 중지됨.")
                        }
                        it.release()
                        Log.d(TAG, "MediaPlayer 리소스 해제됨.")
                    } catch (e: IllegalStateException) {
                        Log.e(TAG, "MediaPlayer 중지/해제 중 오류: ${e.message}", e)
                    }
                }
                mediaPlayer = null
                // --- MediaPlayer 중지 및 해제 끝 ---

                if (_uiState.value.isEmergencyVisualsActive) { // 여전히 긴급 상황 플래그가 켜져 있다면
                    _uiState.value = _uiState.value.copy(
                        isEmergencyVisualsActive = false, // 시각 효과 종료
                        statusMessage = "긴급 상황 대응 시간이 종료되었습니다." // TTS 메시지 변경 가능
                    )
                    startAutoCloseTimer(1000, true) // 잠시 후 IDLE로 전환 유도
                }
            }
            // isEmergencyCommand 블록의 TTS 응답은 위에서 처리하거나, 아래의 공통 showResponse 로직을 사용합니다.
            // 만약 위에서 TTS를 이미 처리했다면, 아래 viewModelScope.launch 블록에서 response 처리가 중복되지 않도록 주의합니다.

        } else { // 일반 명령의 경우
            _uiState.value = _uiState.value.copy(
                currentAiState = AiState.PROCESSING,
                statusMessage = "알겠습니다. \"$text\" 명령을 처리할게요.",
                isEmergencyVisualsActive = false // 일반 명령 시에는 긴급 효과 비활성화
            )
            emergencyVisualsJob?.cancel() // 혹시 모를 긴급 효과 타이머 취소
            // 일반 명령 시에는 MediaPlayer를 여기서 건드릴 필요는 없습니다 (긴급 상황용이므로).
        }

// --- 이 아래는 processVoiceInput 함수의 나머지 부분입니다 ---
// resetAutoCloseTimer(INTERACTION_AUTO_CLOSE_DELAY_MILLIS) // 이 부분은 isEmergencyCommand가 아닐 때만 호출되도록 조정될 수 있습니다.
        if (!isEmergencyCommand) {
            resetAutoCloseTimer(INTERACTION_AUTO_CLOSE_DELAY_MILLIS)
        }

        viewModelScope.launch { // 이 코루틴은 주로 TTS 응답 및 비긴급 명령 처리를 위함
            // 긴급 명령의 경우 TTS는 위 emergencyVisualsJob에서 처리되었을 수 있습니다.
            // 아니라면 여기서 공통으로 처리합니다.
            if (!isEmergencyCommand) { // 일반 명령에 대한 딜레이
                delay(1200)
            }

            // 상태 확인 후 응답 생성
            if (!(_uiState.value.currentAiState == AiState.PROCESSING || (_uiState.value.isEmergencyVisualsActive && _uiState.value.currentAiState == AiState.COMMAND_RECOGNIZED) )) {
                if (!isEmergencyCommand){ // 일반 명령의 경우에만 로그 출력 (긴급은 이미 위에서 처리됨)
                    Log.w(TAG, "processVoiceInput: 응답 생성 전 상태 부적합 (${_uiState.value.currentAiState}, 긴급: ${_uiState.value.isEmergencyVisualsActive}). 응답 생성 중단.")
                }
                return@launch
            }

            // isEmergencyCommand 일 때 TTS 메시지는 emergencyVisualsJob 내에서 설정된 statusMessage를 따르거나, 여기서 다시 정의
            val response = if (isEmergencyCommand) {
                // emergencyVisualsJob 내에서 sirenPlaybackSuccessful 값에 따라 메시지가 이미 statusMessage에 반영되었을 수 있음
                // _uiState.value.statusMessage // 이미 설정된 메시지를 사용하거나,
                if(mediaPlayer != null && mediaPlayer!!.isPlaying) "주변에 긴급 상황을 알립니다!" else "긴급 상황입니다! 사이렌 재생에 문제가 있었습니다." // 여기서 다시 결정
            } else {
                // 기존 일반 명령어 응답 생성 로직
                when {
                    text.contains("119") && (text.contains("전화") || text.contains("연결") || text.contains("구조")) -> {
                        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                            try {
                                val intent = Intent(Intent.ACTION_CALL).apply {
                                    data = android.net.Uri.parse("tel:119")
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
                    text.contains("안녕") -> "안녕하세요! 무엇을 도와드릴까요?"
                    text.contains("이름") || text.contains("누구") -> "저는 당신의 안전을 돕는 랜턴 AI입니다. 잘 부탁드려요."
                    text.contains("날씨") -> "죄송하지만, 현재 날씨 정보를 알려드릴 수는 없어요. 다른 도움이 필요하시면 말씀해주세요."
                    text.contains("시간") -> "지금은 ${java.text.SimpleDateFormat("오후 h시 m분", Locale.KOREAN).format(java.util.Date())} 입니다."
                    text.contains("고마워") || text.contains("감사") -> "천만에요! 언제든지 다시 불러주세요."
                    text.contains("잘가") || text.contains("종료") -> {
                        deactivateAI()
                        "네, 안녕히 가세요."
                    }
                    text.contains("도움") || text.contains("뭐 할 수 있어") -> "저는 음성 명령으로 전화를 걸거나 긴급 상황 시 사이렌을 울릴 수 있어요."
                    else -> "\"$text\"라고 말씀하셨네요. 제가 이해할 수 있는 다른 명령을 내려주시겠어요?"
                }
            }

            // TTS 응답 호출 (긴급 상황이거나 일반 처리 중일 때)
            if (_uiState.value.currentAiState == AiState.PROCESSING || _uiState.value.isEmergencyVisualsActive) {
                showResponse(response)
            } else {
                Log.w(TAG, "processVoiceInput: 응답 생성 후 AI 상태가 유효하지 않음 (${_uiState.value.currentAiState}, 긴급: ${_uiState.value.isEmergencyVisualsActive}). TTS 건너<0xEB><0x9B><0x84>니다.")
            }
        }
    }

    private fun showResponse(response: String) {
        Log.i(TAG, "showResponse: \"$response\"")
        // IDLE 상태이거나, 긴급 시각 효과가 이미 꺼졌는데 응답하려는 경우 등 예외 처리
        if (_uiState.value.currentAiState == AiState.IDLE && !_uiState.value.isEmergencyVisualsActive) {
            Log.w(TAG, "showResponse: AI가 이미 비활성화 상태이므로 TTS 응답을 건너<0xEB><0x9B><0x84>니다.")
            return
        }

        _uiState.value = _uiState.value.copy(
            currentAiState = AiState.SPEAKING,
            statusMessage = response
            // isEmergencyVisualsActive는 이미 processVoiceInput에서 설정됨
        )

        if (ttsInitialized && textToSpeech != null) {
            val result = textToSpeech?.speak(response, TextToSpeech.QUEUE_FLUSH, null, "utteranceId_${System.currentTimeMillis()}")
            if (result == TextToSpeech.ERROR) {
                Log.e(TAG, "TextToSpeech.speak() 호출 실패.")
                showErrorAndPrepareToClose("죄송합니다, 답변을 말씀드리는 데 실패했어요.")
            }
        } else {
            Log.e(TAG, "TTS가 준비되지 않아 응답을 말할 수 없습니다.")
            if (!_uiState.value.isEmergencyVisualsActive) { // 긴급 상황 아닐 때만 자동 닫기
                startAutoCloseTimer(1000, true)
            }
        }
    }

    fun showErrorAndPrepareToClose(message: String) {
        Log.e(TAG, "showErrorAndPrepareToClose: $message")
        // 긴급 시각 효과가 활성 중일 때 에러가 발생하면, 긴급 효과는 유지하되 메시지만 변경하고,
        // 긴급 효과 타이머에 의해 결국 닫히도록 유도하거나, 즉시 닫을지 결정.
        // 여기서는 긴급 효과 중 에러 발생 시, 긴급 효과는 그대로 두고 메시지만 업데이트 후 기존 타이머에 맡김.
        if (_uiState.value.currentAiState == AiState.IDLE && !_uiState.value.isEmergencyVisualsActive) {
            Log.w(TAG, "showErrorAndPrepareToClose: 이미 IDLE 상태이므로 중복 호출 방지.")
            return
        }
        _uiState.value = _uiState.value.copy(
            currentAiState = AiState.ERROR,
            statusMessage = message
            // isEmergencyVisualsActive는 그대로 둠
        )
        // 긴급 시각 효과가 아니라면 일반 에러 타이머 작동
        if (!_uiState.value.isEmergencyVisualsActive) {
            startAutoCloseTimer(ERROR_STATE_AUTO_CLOSE_DELAY_MILLIS, true)
        } else {
            Log.d(TAG, "긴급 시각 효과 중 오류 발생. 긴급 효과 타이머가 AI 종료를 처리할 것임.")
            // 필요하다면 여기서 emergencyVisualsJob을 짧게 설정하여 더 빨리 닫히게 할 수 있음.
            // emergencyVisualsJob?.cancel()
            // emergencyVisualsJob = viewModelScope.launch { delay(1000); deactivateAI(true) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.i(TAG, "onCleared() 호출됨. 모든 리소스 최종 해제.")
        // deactivateAI를 호출하여 모든 잡과 리소스 정리
        deactivateAI(fromTimer = true) // fromTimer true로 하여 모든 것을 정리하도록 유도

        textToSpeech?.shutdown() // TTS 엔진 최종 종료
        textToSpeech = null
        ttsInitialized = false

        // mediaPlayer는 deactivateAI에서 release되므로 여기서 추가 작업 필요 없음
        Log.d(TAG, "onCleared: ViewModel 소멸 및 모든 리소스 해제 완료.")
    }
}