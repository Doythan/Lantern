package com.ssafy.lanterns.ui.screens.call

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.lanterns.data.source.ble.audio.AudioManager
import com.ssafy.lanterns.data.source.ble.BleManager
import com.ssafy.lanterns.ui.screens.main.components.NearbyPerson
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusDecoder
import io.github.jaredmdobson.concentus.OpusEncoder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

private const val SAMPLE_RATE = 24_000
private const val MAX_CALL_REQUEST_TIMEOUT = 30_000L
private const val CONNECTION_CHECK_INTERVAL = 10_000L
private const val TAG = "CallViewModel"


enum class CallState {
    IDLE,               // 통화 없음
    OUTGOING_CALL,      // 통화 요청 중
    INCOMING_CALL,      // 통화 수신 중
    ONGOING_CALL,       // 통화 중
    DISCONNECTED        // 통화 종료됨 (UI 표시용)
}

data class CallUiState(
    val callState: CallState = CallState.IDLE,
    val targetPerson: NearbyPerson? = null,
    val callDuration: Int = 0,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class CallViewModel @Inject constructor(
    private val bleManager: BleManager
) : ViewModel(), BleManager.BleEventListener {
    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())

    private var recorder: AudioRecord? = null
    private var sendThread: Thread? = null
    private var streamTrack: AudioTrack? = null

    private var connectedDevice: String? = null
    private var incomingCallerAddress: String? = null // 추가: 수신된 통화 요청의 발신자 주소 저장
    private var callRequestTimeoutRunnable: Runnable? = null

    private val opusEnc: OpusEncoder = OpusEncoder(SAMPLE_RATE, 1, OpusApplication.OPUS_APPLICATION_AUDIO)
    private val opusDec: OpusDecoder = OpusDecoder(SAMPLE_RATE, 1)

    private var lastHeartbeatTime = 0L
    private var heartbeatMissCount = 0
    private val MAX_HEARTBEAT_MISS = 3

    private var keepConnectionAlive = false

    companion object{
        @Volatile
        private var isCallSessionActive = false

        // 다른 컴포넌트에서 호출 가능한 상태 확인 메서드
        fun isCallActive(): Boolean = isCallSessionActive
    }

    private fun updateCallState(newState: CallState) {
        _uiState.update { it.copy(callState = newState) }

        // 전역 상태 변수 업데이트
        isCallSessionActive = (newState == CallState.ONGOING_CALL)
        Log.d(TAG, "통화 상태 변경: $newState, 활성 상태: $isCallSessionActive")
    }

    init {
        // 초기화 및 리스너 등록
        bleManager.registerListener("CallViewModel", this)
        startConnectionMonitoring()
    }

    private fun startConnectionMonitoring() {
        viewModelScope.launch {
            while (true) {
                bleManager.checkConnectionStatus()
                if (connectedDevice != null && _uiState.value.callState == CallState.ONGOING_CALL) {
                    sendHeartbeat()
                }
                delay(CONNECTION_CHECK_INTERVAL)
            }
        }
    }

    private fun sendHeartbeat() {
        try {
            bleManager.sendAudioData(byteArrayOf(AudioManager.TYPE_HEARTBEAT))
            lastHeartbeatTime = SystemClock.elapsedRealtime()
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat send failure", e)
        }
    }

    /**
     * 타겟 NearbyPerson 설정 (MainViewModel에서 호출)
     */
    fun setTargetPerson(person: NearbyPerson) {
        _uiState.update { it.copy(targetPerson = person) }
    }

    /**
     * 특정 기기에 통화 요청 (서버 ID로)
     */
    fun requestCallToId(serverId: String) {
        if (_uiState.value.callState != CallState.IDLE) {
            Log.d(TAG, "이미 통화 중이거나, 통화 요청 중입니다.")
            return
        }

        // 스캔 시작하여 해당 ID를 가진 기기 찾기
        bleManager.startScanning()
        viewModelScope.launch {
            delay(3000) // 3초 동안 스캔

            // 스캔 결과 확인
            val discoveredDevices = bleManager.getDiscoveredDevices()
            bleManager.stopScanning() // 스캔 중지

            if (discoveredDevices.isEmpty()) {
                _uiState.update { it.copy(
                    errorMessage = "대상 기기를 찾을 수 없습니다.",
                    callState = CallState.DISCONNECTED
                )}
                return@launch
            }

            // 첫 번째 발견된 기기에 연결
            val targetDeviceAddress = discoveredDevices.first()

            // 연결 및 통화 요청
            bleManager.connectToDevice(targetDeviceAddress)
            connectAndRequestCall(targetDeviceAddress)
        }
    }

    /**
     * 특정 기기에 연결하고 통화 요청
     */
    private fun connectAndRequestCall(deviceAddress: String) {
        // 가장 먼저 상태 업데이트를 확실하게 수행
        Log.d(TAG, "통화 발신 상태로 변경: OUTGOING_CALL")
        _uiState.update { it.copy(callState = CallState.OUTGOING_CALL) }

        // 연결 확립 후 통화 요청
        viewModelScope.launch {
            var attempts = 0
            while (!bleManager.isFullyConnected(deviceAddress) && attempts < 20) {
                delay(300)
                attempts++
                Log.d(TAG, "연결 시도 중: $attempts/20, 기본 연결: ${bleManager.isConnected(deviceAddress)}, 완전 연결: ${bleManager.isFullyConnected(deviceAddress)}")

                // 연결 시도 중에도 주기적으로 상태 확인
                if (_uiState.value.callState != CallState.OUTGOING_CALL) {
                    Log.d(TAG, "상태가 변경되어 OUTGOING_CALL로 다시 설정")
                    _uiState.update { it.copy(callState = CallState.OUTGOING_CALL) }
                }
            }

            if (bleManager.isFullyConnected(deviceAddress)) {
                Log.d(TAG, "완전 연결 성공, 통화 요청 시작")
                try {
                    // 상태 재확인 (혹시 변경되었을 수 있으므로)
                    if (_uiState.value.callState != CallState.OUTGOING_CALL) {
                        Log.d(TAG, "상태가 변경되어 OUTGOING_CALL로 다시 설정")
                        _uiState.update { it.copy(callState = CallState.OUTGOING_CALL) }
                    }

                    delay(500)
                    Log.d(TAG, "통화 요청 메시지 전송")
                    bleManager.sendAudioData(byteArrayOf(AudioManager.TYPE_CALL_REQUEST))
                    connectedDevice = deviceAddress // 연결된 기기 주소 저장
                    Log.d(TAG, "통화 요청 메시지 전송 완료, 타임아웃 설정")
                    setCallRequestTimeout()
                } catch (e: Exception) {
                    Log.e(TAG, "통화 요청 실패: ${e.message}", e)
                    _uiState.update { it.copy(
                        errorMessage = "통화 요청 실패: ${e.message}",
                        callState = CallState.DISCONNECTED
                    )}
                }
            } else {
                Log.e(TAG, "연결 시간 초과: $deviceAddress")
                _uiState.update { it.copy(
                    errorMessage = "연결 시간 초과",
                    callState = CallState.DISCONNECTED
                )}
            }
        }
    }

    fun checkAndRestoreConnection() {
        if (_uiState.value.callState == CallState.ONGOING_CALL) {
            Log.d(TAG, "통화 연결 상태 확인 - ${connectedDevice != null}")

            // BleManager 리스너 재등록 (혹시 해제되었다면)
            bleManager.registerListener("CallViewModel", this)

            // 연결 상태 확인
            if (connectedDevice != null) {
                // AudioTrack이 해제되었다면 재생성
                if (streamTrack?.state != AudioTrack.STATE_INITIALIZED ||
                    streamTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {

                    Log.d(TAG, "오디오 트랙 상태 확인 - 비정상, 재시작 시도")

                    try {
                        // 기존 트랙 해제
                        streamTrack?.stop()
                        streamTrack?.release()
                        streamTrack = null

                        // 오디오 스트림 재시작
                        if (sendThread == null || !sendThread!!.isAlive) {
                            Log.d(TAG, "오디오 스트림 재시작")
                            startDuplex()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "오디오 재초기화 실패: ${e.message}")
                    }
                }

                // 클라이언트 연결 확인
                if (!bleManager.isFullyConnected(connectedDevice!!)) {
                    Log.d(TAG, "클라이언트 연결 끊김, 재연결 시도")
                    establishBidirectionalConnection(connectedDevice!!)
                }
            } else if (incomingCallerAddress != null) {
                // 발신자 주소가 있지만 연결이 없는 경우
                Log.d(TAG, "연결이 없지만 발신자 주소가 있음, 연결 시도: $incomingCallerAddress")
                connectedDevice = incomingCallerAddress
                establishBidirectionalConnection(incomingCallerAddress!!)
            }
        }
    }

    private fun setCallRequestTimeout() {
        clearCallRequestTimeout()
        callRequestTimeoutRunnable = Runnable {
            if (_uiState.value.callState == CallState.OUTGOING_CALL) {
                endCall()
                _uiState.update { it.copy(
                    errorMessage = "요청 시간 초과",
                    callState = CallState.DISCONNECTED
                )}
            }
        }
        handler.postDelayed(callRequestTimeoutRunnable!!, MAX_CALL_REQUEST_TIMEOUT)
    }

    private fun clearCallRequestTimeout() {
        callRequestTimeoutRunnable?.let { handler.removeCallbacks(it) }
        callRequestTimeoutRunnable = null
    }

    /**
     * 수신된 통화 요청 수락
     */
    fun acceptCall() {
        if (_uiState.value.callState != CallState.INCOMING_CALL) return

        // 수신된 통화 요청의, 발신자 주소가 저장되어 있어야 함
        val callerAddress = incomingCallerAddress
        if (callerAddress == null) {
            Log.e(TAG, "발신자 주소가 없습니다. 통화 수락 실패")
            _uiState.update { it.copy(
                errorMessage = "발신자 정보가 없습니다.",
                callState = CallState.DISCONNECTED
            )}
            return
        }

        try {
            Log.d(TAG, "통화 수락 처리 시작")

            // 1. 먼저 상태 업데이트 - UI 전환을 빠르게 하기 위함
            _uiState.update { it.copy(callState = CallState.ONGOING_CALL) }

            // 2. 양방향 연결 설정 - 이 함수에서 서버와 클라이언트 역할 모두 수행
            establishBidirectionalConnection(callerAddress)

            // 3. 통화 수락 메시지를 서버와 클라이언트 모두를 통해 전송 (3회씩)
            Log.d(TAG, "통화 수락 메시지 양방향 전송")

            // 3.1 서버를 통해 브로드캐스트 (수신자 -> 발신자)
            for (i in 0 until 3) {
                bleManager.broadcastAudioData(byteArrayOf(AudioManager.TYPE_CALL_ACCEPT))
                Thread.sleep(200)
            }

            // 3.2 클라이언트를 통해 전송 (발신자 -> 수신자)
            for (i in 0 until 3) {
                bleManager.sendAudioData(byteArrayOf(AudioManager.TYPE_CALL_ACCEPT))
                Thread.sleep(200)
            }

            // 4. 오디오 스트림 시작
            startDuplex()
            startCallDurationTimer()

            Log.d(TAG, "통화 수락 처리 완료")
        } catch (e: Exception) {
            Log.e(TAG, "통화 수락 중 오류: ${e.message}", e)
            _uiState.update { it.copy(
                errorMessage = "통화 수락 실패: ${e.message}",
                callState = CallState.DISCONNECTED
            )}
        }
    }

    /**
     * 양방향 연결 수립 (수신자가 발신자에게 연결)
     */
    /**
     * 양방향 연결 수립 - 개선된 버전
     * 발신자와 수신자 모두 서로에게 GATT 클라이언트로 연결하도록 함
     */
    private fun establishBidirectionalConnection(targetAddress: String) {
        Log.d(TAG, "개선된 양방향 연결 설정 시작: $targetAddress")

        // 1. 처음에는 서버 역할도 확실히 시작
        bleManager.initialize() // 서버 시작 확인

        // 2. 즉시 클라이언트로 연결 시도
        BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(targetAddress)?.let { device ->
            Log.d(TAG, "상대방 기기에 클라이언트로 연결 시도: $targetAddress")
            bleManager.connectToDevice(targetAddress)
        }

        // 3. 스캔 및 연결 추가 시도 (비동기)
        viewModelScope.launch {
            try {
                // 기기 스캔
                bleManager.startScanning()
                delay(3000) // 3초 스캔
                val devices = bleManager.getDiscoveredDevices()
                Log.d(TAG, "스캔 결과: ${devices.size}개 기기 발견")
                bleManager.stopScanning()

                if (devices.contains(targetAddress)) {
                    Log.d(TAG, "상대방 기기 발견, 클라이언트로 연결 시도: $targetAddress")

                    // 상대방에게 클라이언트로 연결 시도
                    bleManager.connectToDevice(targetAddress)

                    // 연결 완료될 때까지 대기 (최대 5초)
                    var attempts = 0
                    while (!bleManager.isFullyConnected(targetAddress) && attempts < 20) {
                        delay(250)
                        attempts++
                        Log.d(TAG, "양방향 연결 확인: $attempts/20, 연결됨=${bleManager.isConnected(targetAddress)}, 완전연결=${bleManager.isFullyConnected(targetAddress)}")

                        // 연결이 안되면 재시도
                        if (attempts % 5 == 0 && !bleManager.isConnected(targetAddress)) {
                            Log.d(TAG, "연결 재시도 중...")
                            bleManager.connectToDevice(targetAddress)
                        }
                    }

                    if (bleManager.isFullyConnected(targetAddress)) {
                        Log.d(TAG, "양방향 연결 설정 성공")
                        connectedDevice = targetAddress

                        // 통화 수락 메시지는 양쪽으로 모두 전송 (중요)
                        if (_uiState.value.callState == CallState.ONGOING_CALL) {
                            Log.d(TAG, "연결 후 통화 수락 메시지 양방향 전송")

                            // 클라이언트로 전송
                            bleManager.sendAudioData(byteArrayOf(AudioManager.TYPE_CALL_ACCEPT))
                            delay(100)

                            // 서버로 브로드캐스트
                            bleManager.broadcastAudioData(byteArrayOf(AudioManager.TYPE_CALL_ACCEPT))
                        }
                    } else {
                        Log.e(TAG, "양방향 연결 실패: 시간 초과 (20회 시도)")
                        // 부분 연결 상태라도 진행 시도
                        if (bleManager.isConnected(targetAddress)) {
                            Log.d(TAG, "부분 연결은 성공, 통화 계속 진행")
                            connectedDevice = targetAddress
                        }
                    }
                } else {
                    Log.e(TAG, "상대방 기기를 스캔에서 찾을 수 없음: $targetAddress")

                    // 스캔에서 찾지 못했지만 직접 연결이 이미 성공했을 수 있음
                    if (bleManager.isConnected(targetAddress)) {
                        Log.d(TAG, "스캔 실패했지만 직접 연결은 성공, 계속 진행")
                        connectedDevice = targetAddress
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "양방향 연결 설정 중 오류: ${e.message}", e)
            }
        }
    }

    /**
     * 수신된 통화 요청 거절 또는 현재 통화 종료
     */
    fun endCall() {
        clearCallRequestTimeout()

        // 오디오 스트림 중지
        stopDuplex()

        try {
            // 연결된 기기가 있을 때만 종료 메시지 전송
            if (connectedDevice != null) {
                bleManager.sendAudioData(byteArrayOf(AudioManager.TYPE_CALL_END))
            }

            // 상태 업데이트
            _uiState.update { it.copy(
                callState = CallState.DISCONNECTED,
                callDuration = 0
            )}

            // 연결 유지 모드 해제
            keepConnectionAlive = false

            // 리스너 보존 모드 및 연결 해제 방지 모드 해제
            bleManager.setPreserveCallViewModelListener(false)
            bleManager.setPreventDisconnectDuringCall(false)

            // 모든 연결 해제
            bleManager.disconnectAll()
        } catch (e: Exception) {
            _uiState.update { it.copy(
                callState = CallState.DISCONNECTED,
                errorMessage = "통화 종료 신호 전송 실패: ${e.message}",
                callDuration = 0
            )}
        }
    }

    /**
     * 음소거 상태 토글
     */
    fun toggleMute() {
        _uiState.update { it.copy(isMuted = !it.isMuted) }
    }

    /**
     * 스피커폰 상태 토글
     */
    fun toggleSpeaker() {
        _uiState.update { it.copy(isSpeakerOn = !it.isSpeakerOn) }

        // 스피커폰 설정이 바뀌면 오디오 트랙 재생성
        try {
            streamTrack?.stop()
            streamTrack?.release()
            streamTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "스피커 모드 변경 중 오류", e)
        }
    }

    /**
     * 통화 시간 업데이트 시작
     */
    fun startCallDurationTimer() {
        viewModelScope.launch {
            var duration = 0
            while (_uiState.value.callState == CallState.ONGOING_CALL) {
                delay(1000)
                duration++
                _uiState.update { it.copy(callDuration = duration) }
            }
        }
    }

    // BleManager.BleEventListener 구현
    override fun onDeviceConnected(address: String) {
        Log.d(TAG, "기기 연결됨: $address")
        if (connectedDevice == null) {
            connectedDevice = address
        }
    }

    override fun onDeviceDisconnected(address: String) {
        Log.d(TAG, "기기 연결 해제됨: $address")
        if (connectedDevice == address) {
            connectedDevice = null

            if (_uiState.value.callState == CallState.ONGOING_CALL) {
                stopDuplex()
                _uiState.update { it.copy(
                    callState = CallState.DISCONNECTED,
                    errorMessage = "연결이 끊겼습니다.",
                    callDuration = 0
                )}
            } else if (_uiState.value.callState == CallState.OUTGOING_CALL) {
                _uiState.update { it.copy(
                    callState = CallState.DISCONNECTED,
                    errorMessage = "연결이 끊겼습니다.",
                )}
            }
        }
    }

    override fun onDataReceived(address: String, data: ByteArray) {
        if (data.isEmpty()) return

        try {
            when (data[0]) {
                AudioManager.TYPE_CALL_REQUEST -> handleCallRequest(address)
                AudioManager.TYPE_CALL_ACCEPT -> {
                    Log.d(TAG, "통화 수락 메시지 수신: $address")
                    handleCallAccept(address)
                }
                AudioManager.TYPE_CALL_END -> handleCallEnd(address)
                AudioManager.TYPE_AUDIO_DATA -> handleAudioData(data)
                AudioManager.TYPE_HEARTBEAT -> handleHeartbeat(address)
                AudioManager.TYPE_HEARTBEAT_ACK -> handleHeartbeatAck(address)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onDataReceived 처리 중 오류: ${e.message}", e)
        }
    }

    private fun handleCallRequest(address: String) {
        if (_uiState.value.callState != CallState.IDLE) {
            // 이미 통화 중이면 자동 거절
            try {
                bleManager.sendAudioData(byteArrayOf(AudioManager.TYPE_CALL_END))
            } catch (e: Exception) {
                Log.e(TAG, "자동 거절 메시지 전송 실패", e)
            }
            return
        }

        // 발신자 주소 저장 (수락 시 양방향 연결에 사용됨)
        incomingCallerAddress = address
        Log.d(TAG, "통화 요청을 받음, 발신자 주소 저장: $address")

        // 가상의 NearbyPerson 객체 생성 (실제로는 서버에서 사용자 정보를 가져와야 함)
        val incomingPerson = NearbyPerson(
            serverUserIdString = address,
            nickname = "수신된 통화",
            calculatedVisualDepth = 1,
            advertisedDeviceDepth = 0,
            rssi = -60,
            signalLevel = 3,
            angle = 0f,
            lastSeenTimestamp = System.currentTimeMillis()
        )

        // 상태 업데이트 - 이 부분이 자동으로 수신 화면 전환을 트리거함
        _uiState.update { it.copy(
            callState = CallState.INCOMING_CALL,
            targetPerson = incomingPerson,
            errorMessage = null
        )}

        // 통화 수신 로그
        Log.d(TAG, "통화 수신: $address")
    }

    /**
     * 수신된 통화 수락 메시지 처리
     */
    private fun handleCallAccept(address: String) {
        Log.d(TAG, "통화 수락 메시지 수신 처리 시작: $address, 현재 상태: ${_uiState.value.callState}")

        // 연결된 기기가 존재하면 통화 수락 처리
        // 현재 상태가 ONGOING_CALL 또는 DISCONNECTED가 아닌 경우에만 처리
        if (_uiState.value.callState != CallState.ONGOING_CALL &&
            _uiState.value.callState != CallState.DISCONNECTED) {

            // 타임아웃 취소
            clearCallRequestTimeout()

            // 상태가 IDLE인 경우 로깅
            if (_uiState.value.callState == CallState.IDLE) {
                Log.d(TAG, "상태가 IDLE입니다. 예상치 못한 상태이지만 통화를 계속 진행합니다.")
            }

            // 메인 스레드에서 상태 업데이트를 확실히 실행
            handler.post {
                Log.d(TAG, "메인 스레드에서 통화 상태 업데이트: ONGOING_CALL (이전 상태: ${_uiState.value.callState})")

                // UI 상태 업데이트를 먼저 하여 화면 전환 트리거
                _uiState.update { it.copy(callState = CallState.ONGOING_CALL) }

                // 상태 확인 로그
                Log.d(TAG, "상태 업데이트 직후 확인: ${_uiState.value.callState}")

                // 약간의 지연 후 오디오 초기화 (화면 전환 후)
                handler.postDelayed({
                    try {
                        // recorder, streamTrack이 null인 경우에만 초기화 진행
                        if (recorder == null && sendThread == null) {
                            Log.d(TAG, "통화 시작: 오디오 스트림 초기화")
                            startDuplex()
                            startCallDurationTimer()
                            Log.d(TAG, "통화 시작 완료")
                        } else {
                            Log.d(TAG, "오디오 스트림이 이미 초기화되어 있음. 중복 초기화 방지")
                        }

                        // 상태 재확인
                        Log.d(TAG, "상태 재확인: ${_uiState.value.callState}")
                    } catch (e: Exception) {
                        Log.e(TAG, "통화 시작 실패: ${e.message}", e)
                        _uiState.update { it.copy(
                            errorMessage = "통화 시작 실패: ${e.message}",
                            callState = CallState.DISCONNECTED
                        )}
                    }
                }, 300)
            }
        } else {
            Log.d(TAG, "통화 수락 메시지 무시: 이미 통화 중이거나 종료된 상태입니다 (${_uiState.value.callState})")
        }
    }

    fun onScreenActive() {
        Log.d(TAG, "화면 활성화 신호 수신 - 리스너 및 연결 유지")

        // 통화 중에는 리스너 보존 모드 활성화
        if (_uiState.value.callState == CallState.ONGOING_CALL) {
            bleManager.setPreserveCallViewModelListener(true)
        }

        // BleManager 리스너 재등록
        bleManager.registerListener("CallViewModel", this)

        // 연결이 있지만 오디오 스트림이 없는 경우 재시작
        if (connectedDevice != null && (recorder == null || sendThread == null || !isStreamActive())) {
            Log.d(TAG, "오디오 스트림 재시작 필요")

            // 기존 스트림이 있다면 정리
            try {
                recorder?.stop()
                recorder?.release()
                recorder = null

                sendThread?.interrupt()
                sendThread = null

                streamTrack?.stop()
                streamTrack?.release()
                streamTrack = null
            } catch (e: Exception) {
                Log.e(TAG, "기존 오디오 리소스 정리 중 오류", e)
            }

            // 오디오 스트림 재시작
            startDuplex()
        }

        // 양방향 연결 체크 및 복구
        if (connectedDevice != null && !bleManager.isFullyConnected(connectedDevice!!)) {
            Log.d(TAG, "양방향 연결 복구 시도")
            establishBidirectionalConnection(connectedDevice!!)
        }
    }

    private fun isStreamActive(): Boolean {
        return streamTrack != null &&
                streamTrack?.state == AudioTrack.STATE_INITIALIZED &&
                streamTrack?.playState == AudioTrack.PLAYSTATE_PLAYING
    }

    fun forceOngoingCallState() {
        Log.d(TAG, "forceOngoingCallState 호출: 현재 상태=${_uiState.value.callState}")
        if (_uiState.value.callState != CallState.ONGOING_CALL) {
            handler.post {
                Log.d(TAG, "강제로 ONGOING_CALL 상태로 설정 (이전: ${_uiState.value.callState})")
                _uiState.update { it.copy(callState = CallState.ONGOING_CALL) }
            }
        }
    }

    fun triggerStateUpdate() {
        Log.d(TAG, "상태 업데이트 트리거: ${_uiState.value.callState}")
        // 현재 상태를 복사해서 동일한 상태로 다시 설정
        // 이렇게 하면 UI 쪽에서 상태 변경을 다시 감지함
        val currentState = _uiState.value
        _uiState.update { currentState.copy() }
    }

    private fun handleCallEnd(address: String) {
        clearCallRequestTimeout()
        if (_uiState.value.callState == CallState.ONGOING_CALL) {
            stopDuplex()
            _uiState.update { it.copy(
                callState = CallState.DISCONNECTED,
                errorMessage = "상대방이 통화를 종료했습니다.",
                callDuration = 0
            )}
        } else if (_uiState.value.callState == CallState.OUTGOING_CALL) {
            _uiState.update { it.copy(
                callState = CallState.DISCONNECTED,
                errorMessage = "상대방이 통화를 거절했습니다."
            )}
        } else if (_uiState.value.callState == CallState.INCOMING_CALL) {
            _uiState.update { it.copy(
                callState = CallState.DISCONNECTED,
                errorMessage = "상대방이 통화 요청을 취소했습니다."
            )}
        }

        // 연결 정보 초기화
        connectedDevice = null
        incomingCallerAddress = null
    }

    private fun handleHeartbeat(address: String) {
        try {
            bleManager.sendAudioData(byteArrayOf(AudioManager.TYPE_HEARTBEAT_ACK))
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat ACK send failure", e)
        }
    }

    private fun handleHeartbeatAck(address: String) {
        heartbeatMissCount = 0
    }

    @SuppressLint("MissingPermission")
    private fun startDuplex() {
        try {
            // 이미 AUDIO로 설정되어 있으므로 다른 매개변수만 조정
            opusEnc.setBitrate(32000) // 비트레이트 증가 (음질 향상)
            opusEnc.setComplexity(10)  // 인코딩 품질 증가 (CPU 사용량 약간 증가)

            opusEnc.setForceChannels(1) // 모노 채널 강제 (필요한 경우)
            Log.d(TAG, "Opus 인코더 추가 최적화 설정 완료")
        } catch (e: Exception) {
            Log.e(TAG, "Opus 설정 실패", e)
        }
        // 이미 실행 중이면 중복 실행 방지
        if (recorder != null || sendThread != null) {
            Log.d(TAG, "오디오 스트림이 이미 실행 중입니다. 중복 시작 방지")
            return
        }

        bleManager.setPreserveCallViewModelListener(true)
        bleManager.setPreventDisconnectDuringCall(true)
        bleManager.disablePeriodicScanning()

        val frameSize = SAMPLE_RATE / 1000 * 20
        val bufSize = frameSize * 2

        try {
            Log.d(TAG, "오디오 녹음 시작: 샘플레이트=$SAMPLE_RATE, 프레임크기=$frameSize")
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize
            ).apply { startRecording() }

            sendThread = Thread {
                val pcm = ByteArray(bufSize)
                val opus = ByteArray(4000) // Opus 인코딩 데이터 버퍼
                try {
                    var framesProcessed = 0
                    while (_uiState.value.callState == CallState.ONGOING_CALL &&
                        recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val read = recorder!!.read(pcm, 0, bufSize)

                        // 음소거 상태면 오디오 데이터 전송 안함
                        if (_uiState.value.isMuted) {
                            Thread.sleep(20)
                            continue
                        }

                        if (read == bufSize) {
                            val ts = SystemClock.uptimeMillis().toInt()
                            val header = ByteBuffer.allocate(5)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .put(AudioManager.TYPE_AUDIO_DATA)
                                .putInt(ts)
                                .array()
                            try {
                                val encLen = opusEnc.encode(pcm, 0, frameSize, opus, 0, opus.size)
                                val encodedData = header + opus.copyOf(encLen)

                                try {
                                    // 순서 변경: 먼저 클라이언트로 전송
                                    bleManager.sendAudioData(encodedData)
                                } catch (e: Exception) {
                                    // 로그 생략 - 성능 저하 방지
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "오디오 인코딩/전송 오류: ${e.message}", e)
                            }
                            Thread.sleep(1)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "오디오 스레드 오류: ${e.message}", e)
                } finally {
                    Log.d(TAG, "오디오 전송 스레드 종료")
                }
            }.also {
                it.name = "AudioSendThread"
                it.start()
                Log.d(TAG, "오디오 전송 스레드 시작")
            }
        } catch (e: Exception) {
            Log.e(TAG, "오디오 초기화 실패: ${e.message}", e)
            _uiState.update { it.copy(
                errorMessage = "오디오 초기화 실패: ${e.message}",
                callState = CallState.DISCONNECTED
            )}
        }
    }

    private fun stopDuplex() {
        try {
            recorder?.run { if (recordingState == AudioRecord.RECORDSTATE_RECORDING) stop() }
            recorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Recorder cleanup error", e)
        } finally {
            recorder = null
        }

        try { sendThread?.interrupt() } catch (e: Exception) {
            Log.e(TAG, "Thread interrupt error", e)
        } finally {
            sendThread = null
        }

        try {
            streamTrack?.stop()
            streamTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Audio track cleanup error", e)
        } finally {
            streamTrack = null
        }
    }

    private fun handleAudioData(data: ByteArray) {
        try {

            val enc = data.copyOfRange(5, data.size)
            val pcmBuf = ShortArray(1920) // 24kHz, 20ms 스테레오 최대 샘플
            try {
                val outCount = opusDec.decode(enc, 0, enc.size, pcmBuf, 0, pcmBuf.size, false)

                val pcm = ByteBuffer.allocate(outCount * 2)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .apply { for (i in 0 until outCount) putShort(pcmBuf[i]) }
                    .array()

                handler.post { playChunk(pcm) }
            } catch (e: Exception) {
                Log.e(TAG, "오디오 디코딩 오류: ${e.message}", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "오디오 데이터 처리 오류: ${e.message}", e)
        }
    }

    private fun playChunk(chunk: ByteArray) {
        if (_uiState.value.callState != CallState.ONGOING_CALL) {
            return
        }

        try {
            if (streamTrack == null ||
                streamTrack?.state != AudioTrack.STATE_INITIALIZED ||
                streamTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {

                try {
                    if (streamTrack != null) {
                        streamTrack?.flush()
                        streamTrack?.stop()
                        streamTrack?.release()
                        streamTrack = null
                    }

                    val minBuf = AudioTrack.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )

                    // 버퍼 크기 조정 - 테스트 코드 값을 참고하되 안전하게 유지
                    val bufferSize = maxOf(minBuf * 4, chunk.size * 6)

                    val usage = if (_uiState.value.isSpeakerOn)
                        AudioAttributes.USAGE_MEDIA
                    else
                        AudioAttributes.USAGE_VOICE_COMMUNICATION

                    streamTrack = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(usage)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setBufferSizeInBytes(bufferSize)
                        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                        .build()

                    if (streamTrack?.state == AudioTrack.STATE_INITIALIZED) {
                        streamTrack?.play()
                        if (!_uiState.value.isSpeakerOn) {
                            // 볼륨 값을 크게 설정 (2.0f는 두 배 볼륨을 의미)
                            // 값을 조절하며 최적 볼륨 찾기 (1.5f ~ 4.0f 범위에서 실험)
                            streamTrack?.setVolume(3.0f)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "AudioTrack 생성 실패: ${e.message}", e)
                    return
                }
            }

            try {
                val currentTrack = streamTrack
                if (currentTrack != null && currentTrack.state == AudioTrack.STATE_INITIALIZED) {
                    // AudioTrack 재생 중이 아니면 재시작
                    if (currentTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        try {
                            currentTrack.play()
                        } catch (e: Exception) {
                            Log.e(TAG, "AudioTrack 재생 시작 실패", e)
                        }
                    }

                    // 여기서는 WRITE_BLOCKING 유지 - 안정성 위해
                    val written = currentTrack.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)

                    // 로깅 최소화 - 오류 발생 시에만 로그
                    if (written <= 0) {
                        Log.e(TAG, "오디오 쓰기 실패: $written")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "오디오 재생 오류: ${e.message}", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "playChunk 전체 오류: ${e.message}", e)
        }
    }

    /**
     * 통화 오류 메시지 초기화
     */
    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 통화 상태 리셋 (DISCONNECTED → IDLE)
     */
    fun resetCallState() {
        if (_uiState.value.callState == CallState.DISCONNECTED) {
            // 리스너 보존 모드 해제
            bleManager.setPreserveCallViewModelListener(false)

            _uiState.update { it.copy(
                callState = CallState.IDLE,
                targetPerson = null,
                errorMessage = null,
                callDuration = 0,
                isMuted = false,
                isSpeakerOn = false
            )}
        }
    }

    fun setKeepConnectionAlive(keep: Boolean) {
        keepConnectionAlive = keep
        Log.d(TAG, "연결 유지 모드 설정: $keep")

        if (keep) {
            // 리스너 보존 모드와 연결 해제 방지 모드 활성화
            bleManager.setPreserveCallViewModelListener(true)
            bleManager.setPreventDisconnectDuringCall(true)

            // 리스너 다시 등록
            bleManager.registerListener("CallViewModel", this)
        }
    }


    // onCleared() 메서드 보강
    override fun onCleared() {
        Log.d(TAG, "CallViewModel.onCleared() 호출됨 - keepConnectionAlive: $keepConnectionAlive, 현재 상태: ${_uiState.value.callState}")
        super.onCleared()

        // 통화 중이면 무조건 연결 유지
        if (_uiState.value.callState == CallState.ONGOING_CALL) {
            Log.d(TAG, "통화 중 onCleared() - 연결 유지 모드 강제 활성화")
            keepConnectionAlive = true
            bleManager.setPreserveCallViewModelListener(true)
            bleManager.setPreventDisconnectDuringCall(true)
        }

        // 연결 유지 모드가 아닐 때만 리소스 정리
        if (!keepConnectionAlive) {
            stopDuplex()
            bleManager.setPreserveCallViewModelListener(false)
            bleManager.setPreventDisconnectDuringCall(false)
            bleManager.unregisterListener("CallViewModel")
            bleManager.disconnectAll()
        } else {
            Log.d(TAG, "연결 유지 모드 활성화됨 - 리소스 유지")
        }

        handler.removeCallbacksAndMessages(null)
    }
}