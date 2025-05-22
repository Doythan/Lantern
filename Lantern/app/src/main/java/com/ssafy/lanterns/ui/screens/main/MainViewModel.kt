package com.ssafy.lanterns.ui.screens.main

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.ssafy.lanterns.config.BleConstants
import com.ssafy.lanterns.config.NeighborDiscoveryConstants
import com.ssafy.lanterns.data.model.User
import com.ssafy.lanterns.data.repository.UserRepository
import com.ssafy.lanterns.di.EmergencyEventTrigger
import com.ssafy.lanterns.service.ble.advertiser.NeighborAdvertiser
import com.ssafy.lanterns.service.ble.scanner.NeighborScanner
import com.ssafy.lanterns.ui.screens.call.CallViewModel
import com.ssafy.lanterns.ui.screens.main.components.NearbyPerson
import com.ssafy.lanterns.utils.SignalStrengthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import kotlin.math.abs
import java.lang.ref.WeakReference


data class MainScreenUiState(
    val isScanningActive: Boolean = false,
    val nearbyPeople: List<NearbyPerson> = emptyList(),
    val showPersonListModal: Boolean = false,
    val buttonText: String = "탐색 시작",
    val blePermissionsGranted: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val isBleReady: Boolean = false,
    val navigateToProfileServerUserIdString: String? = null,
    val displayDepthLevel: Int = NeighborDiscoveryConstants.MAX_DISPLAY_DEPTH_INITIAL,
    val errorMessage: String? = null,
    val isLoading: Boolean = true,
    val currentSelfAdvertisedDepth: Int = 0,
    val subTextVisible: Boolean = true,
    val showListButton: Boolean = false,
    val showEmergencyAlert: Boolean = false,
    val emergencyAlertNickname: String? = null,
    val isEmergencyVisualEffectActive: Boolean = false,
    val currentEmergencyPacketTimestamp: Long = 0L,
    val rescueRequestReceived: Boolean = false,
    val rescueRequesterNickname: String? = null,
    val showProfileModal: Boolean = false,
    val selectedProfilePerson: NearbyPerson? = null,
    val userProfileImageNumber: Int = 1 // 사용자 프로필 이미지 번호 (기본값 1)
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val userRepository: UserRepository,
    @EmergencyEventTrigger private val emergencyEventTriggerFlow: SharedFlow<Unit>
) : ViewModel() {
    companion object { private const val TAG = "MainVM_Emergency" }
    private val _uiState = MutableStateFlow(MainScreenUiState())
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()


    private val _isEmergencyBroadcastingActive = MutableStateFlow(false)


    private val _aiActive = MutableStateFlow(false)
    val aiActive: StateFlow<Boolean> = _aiActive.asStateFlow()
    private var lastAiActivationTime = 0L

    private var bleOperationJob: Job? = null
    private var myServerUserId: Long = -1L
    private var myNickname: String = "랜턴" // 기본값
    private var myPreparedNicknameForAdv: String = "랜턴" // 광고용으로 준비된 닉네임
    @Volatile private var myCurrentAdvertisedDepth: Int = 0 // 항상 0으로 초기화

    private val bleHandler = Handler(Looper.getMainLooper())
    private val advertiseRunnable: Runnable = Runnable { startOrUpdateAdvertising(isEmergency = 0.toByte()) }
    private var activityRef: WeakReference<Activity>? = null

    private val lastUIUpdatedTimestamps = mutableMapOf<String, Long>() // 마지막 UI 업데이트 시간 저장
    // 최적화된 UI 업데이트 간격 (1초)
    private val UI_UPDATE_INTERVAL_SHORT_MS = 10000L 

    init {
        Log.d(TAG, "MainViewModel init 시작")
        observeEmergencyEvents()
    }

    private var lastEmergencyEventTime = 0L
    private fun observeEmergencyEvents() {
        viewModelScope.launch {
            emergencyEventTriggerFlow.collect {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastEmergencyEventTime > 1000) { // 예: 1초 이내 중복 이벤트 무시
                    lastEmergencyEventTime = currentTime
                    Log.d(TAG, "!!! MainViewModel에서 긴급 이벤트 수신됨 !!! triggerEmergencyBroadcast() 호출 예정.")
                    triggerEmergencyBroadcast()
                } else {
                    Log.d(TAG, "MainViewModel에서 짧은 시간 내 중복 긴급 이벤트 수신 무시됨.")
                }
            }
        }
    }


    fun initialize(activity: Activity) {
        activityRef = WeakReference(activity)
        Log.i(TAG, "ViewModel 초기화 시작")
        updateBluetoothStateAndPermissions(activity) // 최초 상태 확인

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val currentUser: User? = userRepository.getCurrentUser()
            if (currentUser != null && currentUser.userId != 0L) {
                myServerUserId = currentUser.userId
                myNickname = currentUser.nickname.ifBlank { "랜턴_${currentUser.userId.toString().takeLast(3)}" }
                myPreparedNicknameForAdv = truncateNicknameForAdv(myNickname)
                
                // 사용자 프로필 이미지 번호 로드
                val profileImageNumber = currentUser.selectedProfileImageNumber
                
                Log.i(TAG, "사용자 정보 로드: sID=$myServerUserId, Nick='$myNickname', ProfileImg=$profileImageNumber, MyAdvDepth=$myCurrentAdvertisedDepth")
                _uiState.update { it.copy(
                    currentSelfAdvertisedDepth = myCurrentAdvertisedDepth, 
                    userProfileImageNumber = profileImageNumber, 
                    isLoading = false
                ) }
            } else {
                _uiState.update { it.copy(errorMessage = "로그인 정보가 유효하지 않습니다. 탐색을 시작할 수 없습니다.", isLoading = false) }
                Log.w(TAG, "유효한 사용자 정보 없음. currentUser: $currentUser")
                myServerUserId = -1L
            }
            checkBleReadyState() // 사용자 정보 로드 후 최종 BLE 준비 상태 확인
        }
    }

    private fun vibratePhone() {
        val vibrator = applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        if (vibrator?.hasVibrator() == true) { // 진동기 있는지 확인 (선택적)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android Oreo (API 26) 이상에서는 VibrationEffect 사용

                val timings = longArrayOf(0, 100, 100, 100, 100, 100) // 대기, 진동, 대기, 진동, ... (ms)
                val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE) // 각 타이밍에 대한 진동 강도 (0은 끔)
                val vibrationEffect = VibrationEffect.createWaveform(timings, amplitudes, -1) // -1은 반복 안 함
                vibrator.vibrate(vibrationEffect)

                Log.d(TAG, "진동 발생 (Oreo 이상)")
            } else {
                // Oreo 미만에서는 deprecated된 vibrate 사용
                @Suppress("DEPRECATION")
                vibrator.vibrate(500) // 500ms 동안 진동
                Log.d(TAG, "진동 발생 (Oreo 미만)")
            }
        } else {
            Log.w(TAG, "기기에 진동 기능이 없거나 사용할 수 없습니다.")
        }
    }


    private fun truncateNicknameForAdv(nickname: String, maxBytes: Int = BleConstants.MAX_NICKNAME_BYTES_ADV): String {
        val originalBytes = nickname.toByteArray(StandardCharsets.UTF_8)
        if (originalBytes.size <= maxBytes) {
            return nickname
        }

        // maxBytes를 넘지 않는 가장 긴 부분 문자열 찾기
        var endIndex = 0
        var currentBytes = 0
        for (i in nickname.indices) {
            // 현재 문자를 바이트로 변환하여 크기 확인
            val charAsBytes = nickname.substring(i, i + 1).toByteArray(StandardCharsets.UTF_8)
            if (currentBytes + charAsBytes.size > maxBytes) {
                break // 현재 문자를 추가하면 maxBytes를 초과하므로 여기서 중단
            }
            currentBytes += charAsBytes.size
            endIndex = i + 1
        }
        val truncatedNickname = nickname.substring(0, endIndex)
        Log.d(TAG, "닉네임 절단: 원본='$nickname'(${originalBytes.size}b) -> 절단본='$truncatedNickname'(${truncatedNickname.toByteArray(StandardCharsets.UTF_8).size}b), 최대 ${maxBytes}b")
        return truncatedNickname
    }

    fun updateBlePermissionStatus(granted: Boolean) {
        Log.d(TAG, "BLE 권한 상태 업데이트: $granted")
        val newState = _uiState.value.copy(blePermissionsGranted = granted)
        _uiState.value = newState // 직접 할당하여 즉시 반영
        checkBleReadyState()
    }

    fun updateBluetoothState(enabled: Boolean) {
        Log.d(TAG, "블루투스 활성화 상태 업데이트: $enabled")
        val newState = _uiState.value.copy(isBluetoothEnabled = enabled)
        _uiState.value = newState // 직접 할당
        checkBleReadyState()
    }

    // BLE 준비 상태만 확인하고 isBleReady 상태를 업데이트합니다.
    private fun checkBleReadyState() {
        val currentState = _uiState.value
        val isReady = currentState.blePermissionsGranted && currentState.isBluetoothEnabled && myServerUserId != -1L
        Log.d(TAG, "BLE 준비 상태 확인: isReady=$isReady (권한=${currentState.blePermissionsGranted}, BT=${currentState.isBluetoothEnabled}, 로그인=${myServerUserId != -1L})")

        if (currentState.isBleReady != isReady) {
             _uiState.update { it.copy(isBleReady = isReady) }
        }

        // 준비 상태가 아니라면 에러 메시지 설정
        if (!isReady) {
            val errorMsg = when {
                myServerUserId == -1L -> _uiState.value.errorMessage ?: "로그인 정보가 필요합니다."
                !currentState.blePermissionsGranted -> "탐색을 위해 BLE 권한이 필요합니다."
                !currentState.isBluetoothEnabled -> "탐색을 위해 블루투스를 켜주세요."
                else -> null
            }
            if (_uiState.value.errorMessage != errorMsg) {
                _uiState.update { it.copy(errorMessage = errorMsg) }
            }
            if (_uiState.value.isScanningActive) { // 준비 안되면 스캔 중지
                stopBleOperationsInternal()
                _uiState.update { it.copy(isScanningActive = false, buttonText = "탐색 시작", nearbyPeople = emptyList(), showListButton = false) }
            }
        } else {
            // BLE가 준비되면 오류 메시지 초기화 (만약 있었다면)
            if (_uiState.value.errorMessage != null) {
                _uiState.update { it.copy(errorMessage = null)}
            }
        }
    }

    fun toggleScan() {
        val currentState = _uiState.value
        Log.d(TAG, "toggleScan 호출됨. 현재 스캔 상태: ${currentState.isScanningActive}, BLE 준비 상태: ${currentState.isBleReady}")
        if (currentState.isScanningActive) {
            stopBleOperationsInternal()
            _uiState.update { it.copy(isScanningActive = false, buttonText = "탐색 시작", nearbyPeople = emptyList(), showListButton = false) }
        } else {
            if (currentState.isBleReady) {
                _uiState.update { it.copy(isScanningActive = true, buttonText = "탐색 중...", errorMessage = null) }
                startBleOperationsInternal()
            } else {
                Log.w(TAG, "BLE 준비 안됨, 스캔 시작 불가. (권한=${currentState.blePermissionsGranted}, BT=${currentState.isBluetoothEnabled}, 로그인=${myServerUserId != -1L})")
                // 사용자가 명시적으로 스캔을 시도했으므로, 상태 재확인 및 필요시 권한/설정 안내
                activityRef?.get()?.let { updateBluetoothStateAndPermissions(it) } ?: checkBleReadyState() // Activity 없으면 상태만 재확인
            }
        }
    }

    private fun startBleOperationsInternal() {
        if (myServerUserId == -1L) { Log.w(TAG, "서버 ID 없어 BLE 작업 시작 불가"); return }
        if (!_uiState.value.isBleReady) { Log.w(TAG, "BLE 준비 안되어 작업 시작 불가"); return}

        activityRef?.get()?.let {
            NeighborAdvertiser.init(it)
            NeighborScanner.init(it)
            NeighborScanner.setMyNickname(myNickname)
            NeighborScanner.setMyServerId(myServerUserId)
        } ?: run { Log.e(TAG, "Activity Context null, BLE 초기화 불가"); return }

        Log.i(TAG, "BLE 작업 시작 요청됨")
        // _uiState.update { it.copy(buttonText = "탐색 중...", showListButton = _uiState.value.nearbyPeople.isNotEmpty()) } // 토글에서 이미 처리

        NeighborScanner.startScanning()
        startOrUpdateAdvertising(isEmergency = 0.toByte()) // 즉시 광고 및 주기적 업데이트 예약

        bleOperationJob?.cancel()
        bleOperationJob = viewModelScope.launch {
            Log.d(TAG, "BLE 스캔 결과 처리 코루틴 시작, 갱신 주기: ${NeighborDiscoveryConstants.BLE_SCAN_INTERVAL_MS}ms")
            while (_uiState.value.isScanningActive) {
                processScanResults()
                delay(NeighborDiscoveryConstants.BLE_SCAN_INTERVAL_MS)
            }
        }
    }

    private fun stopBleOperationsInternal() {
        Log.i(TAG, "BLE 작업 중지 요청됨")
        bleOperationJob?.cancel()
        bleHandler.removeCallbacks(advertiseRunnable)
        NeighborScanner.stopScanning()
        NeighborAdvertiser.stopAdvertising()
    }

    fun onScreenResumed() {
        Log.d(TAG, "화면 재개: 현재 스캔 상태: ${_uiState.value.isScanningActive}, BLE 준비 상태: ${_uiState.value.isBleReady}")
        activityRef?.get()?.let {
            updateBluetoothStateAndPermissions(it) // 화면 재개 시 항상 최신 상태로 업데이트
        } ?: Log.w(TAG, "Activity 참조가 없어 화면 재개 시 상태 업데이트 못함")
        
        // isBleReady 상태는 updateBluetoothStateAndPermissions -> checkBleReadyState 를 통해 업데이트됨
        // isScanningActive 상태는 이전 상태를 유지하려고 시도
        if (_uiState.value.isScanningActive && _uiState.value.isBleReady) {
            Log.i(TAG, "화면 재개: BLE 준비 및 이전 스캔 활성 상태이므로 스캔 재시작")
            startBleOperationsInternal()
        } else if (_uiState.value.isScanningActive && !_uiState.value.isBleReady){
             Log.w(TAG, "화면 재개: 스캔은 활성이었으나 BLE 준비 안됨. 스캔 시작 안함.")
             // 사용자가 다시 스캔 버튼을 누르도록 유도하거나, 에러 메시지를 통해 상태 알림
             _uiState.update { it.copy(isScanningActive = false, buttonText = "탐색 시작") }
        }
    }

    fun onScreenPaused() {
        Log.d(TAG, "화면 일시정지: 통화 상태 확인")

        // CallViewModel 통화 상태 확인 (static 메서드 사용)
        val isCallActive = try {
            // 통화 활성 상태 확인
            com.ssafy.lanterns.ui.screens.call.CallViewModel.isCallActive()
        } catch (e: Exception) {
            Log.e(TAG, "통화 상태 확인 실패", e)
            false
        }

        if(isCallActive) {
            Log.d(TAG, "통화가 활성 상태이므로 BLE 작업 중지하지 않음")
            return
        }

        // 화면 일시정지 처리 (통화 중이 아닐 때만)
        Log.d(TAG, "화면 일시정지: BLE 작업 중지")
        if(_uiState.value.isScanningActive){
            stopBleOperationsInternal()
            _uiState.update { it.copy(isScanningActive = false, buttonText = "탐색 시작") }
        }
    }

    private fun startOrUpdateAdvertising(isEmergency: Byte = 0.toByte()) {
        if (myServerUserId == -1L || !_uiState.value.isBleReady) return

        // 프로필 이미지 번호 추가하여 전달
        val profileImageNumber = _uiState.value.userProfileImageNumber
        NeighborAdvertiser.startAdvertising(myServerUserId, myPreparedNicknameForAdv, myCurrentAdvertisedDepth, isEmergency, profileImageNumber)
        
        if (isEmergency == 0.toByte()) {
            bleHandler.removeCallbacks(advertiseRunnable) // 이전 예약이 있다면 취소
            bleHandler.postDelayed(advertiseRunnable, NeighborDiscoveryConstants.ADVERTISE_INTERVAL_MS)
        }
    }



    // MainViewModel.kt
    // MainViewModel.kt
    fun triggerEmergencyBroadcast() {
        Log.d(TAG, ">>> MainViewModel.triggerEmergencyBroadcast() 함수 요청됨 <<<")
        viewModelScope.launch {
            if (_isEmergencyBroadcastingActive.value) {
                Log.w(TAG, "이미 긴급 방송이 활성화되어 있어 중복 요청 무시됨.")
                return@launch
            }

            Log.d(TAG, "triggerEmergencyBroadcast 코루틴 시작")
            var attempts = 0
            // isBleReady와 myServerUserId가 준비될 때까지 대기 (최대 3초)
            while ((!_uiState.value.isBleReady || myServerUserId == -1L) && attempts < 30) {
                Log.w(TAG, "triggerEmergencyBroadcast 대기 중: isBleReady=${_uiState.value.isBleReady}, myServerUserId=$myServerUserId (시도: $attempts)")
                delay(100)
                attempts++
            }

            if (myServerUserId == -1L || !_uiState.value.isBleReady) {
                Log.e(TAG, "긴급 광고 시작 불가 (최종 확인): 서버 ID ($myServerUserId) 또는 BLE 준비 안됨 (isBleReady=${_uiState.value.isBleReady})")
                _uiState.update { it.copy(errorMessage = "긴급 호출을 전송할 수 없습니다. 초기화가 완료되지 않았거나 BLE 상태를 확인해주세요.") }
                return@launch
            }

            // --- 긴급 광고 시작 ---
            _isEmergencyBroadcastingActive.value = true
            Log.i(TAG, "긴급 광고 시작: 사용자 ID=$myServerUserId, 닉네임='$myPreparedNicknameForAdv', Depth=$myCurrentAdvertisedDepth")

            // 기존 주기적 일반 광고 핸들러 중지
            withContext(Dispatchers.Main) {
                bleHandler.removeCallbacks(advertiseRunnable)
            }
            Log.d(TAG, "기존 일반 광고 핸들러(advertiseRunnable) 제거됨.")

            Log.d(TAG, "NeighborAdvertiser.startAdvertising 호출 (Emergency=1)")
            NeighborAdvertiser.startAdvertising(
                myServerUserId, 
                myPreparedNicknameForAdv, 
                myCurrentAdvertisedDepth, 
                isEmergency = 1.toByte(),
                profileImageNumber = _uiState.value.userProfileImageNumber
            )

            val emergencyDuration = 10000L // 10초 (또는 원하는 시간으로 조절)
            Log.d(TAG, "${emergencyDuration / 1000}초 동안 긴급 광고 유지 예정.")
            delay(emergencyDuration)

            // --- 긴급 광고 종료 및 일반 광고로 복귀 또는 중단 ---
            Log.i(TAG, "긴급 광고 시간(${emergencyDuration / 1000}초) 종료.")

            val currentlyScanning = _uiState.value.isScanningActive
            val currentlyBleReady = _uiState.value.isBleReady

            if (currentlyScanning && currentlyBleReady) {
                Log.d(TAG, "일반 광고로 복귀 시도. (스캔: $currentlyScanning, 준비: $currentlyBleReady)")
                startOrUpdateAdvertising(isEmergency = 0.toByte()) // 일반 광고 시작 및 주기적 업데이트 재개
            } else {
                Log.w(TAG, "긴급 광고 종료 후 일반 광고 전환 조건 미충족. 광고 완전 중단 시도. (스캔: $currentlyScanning, 준비: $currentlyBleReady)")
                NeighborAdvertiser.stopAdvertising()
                Log.d(TAG, "NeighborAdvertiser.stopAdvertising() 호출로 모든 광고 명시적 중단.")
            }
            _isEmergencyBroadcastingActive.value = false // 긴급 광고 상태 확실히 해제
        }
    }

    private val lastEmergencyNotificationTimestamps = mutableMapOf<String, Long>()
    private val EMERGENCY_NOTIFICATION_COOLDOWN_MS = 30 * 60 * 1000L
    private val EMERGENCY_ALERT_AUTO_DISMISS_MS = 5 * 1000L // 긴급 UI 알림 자동 해제 시간 (15초)
    private val EMERGENCY_RSSI_THRESHOLD = -80


    private fun processScanResults() {
        if (myServerUserId == -1L) return

        val currentTime = System.currentTimeMillis()
        val updatedNearbyMap = mutableMapOf<String, NearbyPerson>()
        val currentScannedDevices = NeighborScanner.scannedDevicesMap.toMap()
        Log.d(TAG, "현재 스캔된 디바이스 수: ${currentScannedDevices.size}")

        // 내 홉수 계산을 위한 변수
        var foundAnchorDevice = false
        var shortestPathToRootViaNeighbor = -1

        var newMyCurrentAdvertisedDepth = myCurrentAdvertisedDepth // 현재 값으로 초기화
        var newFoundAnchorDevice = false
        var newShortestPathToRootViaNeighbor = if (myCurrentAdvertisedDepth == 0) -1 else myCurrentAdvertisedDepth // 초기값 설정


        currentScannedDevices.forEach { (bleAddress, scannedDevice) ->
            // 자신의 기기는 무시
            if (scannedDevice.serverUserId == myServerUserId) return@forEach

            // 시간 초과된 기기 제거 - NeighborScanner와 동일한 시간 사용
            if (currentTime - scannedDevice.lastSeen > NeighborScanner.DEVICE_EXPIRATION_MS) {
                // Log.d(TAG, "기기 시간 초과 (UI): '${scannedDevice.nickname}'(${scannedDevice.serverUserId}), 마지막 발견: ${(currentTime - scannedDevice.lastSeen) / 1000}초 전")
                NeighborScanner.scannedDevicesMap.remove(bleAddress)
                SignalStrengthManager.clearHistoryForDevice(bleAddress)
                return@forEach
            }
            
            // 긴급 플래그 확인 및 처리
            if (scannedDevice.isEmergency == 1.toByte()) {
                Log.i(TAG, "긴급 패킷 수신: Nick='${scannedDevice.nickname}', sID=${scannedDevice.serverUserId}, RSSI=${scannedDevice.rssi}")

                val serverUserIdStr = scannedDevice.serverUserId.toString()
                val lastNotificationTime = lastEmergencyNotificationTimestamps[serverUserIdStr] ?: 0L

                if (currentTime - lastNotificationTime > EMERGENCY_NOTIFICATION_COOLDOWN_MS) {

                    // 거리 기반 필터링 (예: RSSI 임계값 사용)
                    // 실제 50m는 RSSI만으로 정확히 판단하기 어려우므로, 여기서는 예시로 강한 신호(-70 이상)일 때 알림
                    if (scannedDevice.rssi >= -70) { // 이 임계값은 테스트를 통해 조정 필요
                        Log.d(
                            TAG,
                            "수신: 새로운 긴급 구조 요청 알림 표시 예정. Nick='${scannedDevice.nickname}' (쿨다운 통과)"
                        )
                        // 중복 알림 방지 로직 (currentEmergencyPacketTimestamp와 lastSeen 비교)
                        // 또는 특정 시간 내 동일 ID 알림 무시 등

                        _uiState.update {
                            it.copy(
                                // showEmergencyAlert = true, // 이 변수는 이전의 다른 시각/청각 효과용으로 남겨두거나
                                // rescueRequestReceived와 통합 고려.
                                // 여기서는 새로운 UI 상태 변수를 사용한다고 가정.
                                rescueRequestReceived = true,
                                rescueRequesterNickname = scannedDevice.nickname
                                // isEmergencyVisualEffectActive = true, // 필요시 이 효과도 다시 활성화
                                // currentEmergencyPacketTimestamp = scannedDevice.lastSeen // 이 변수의 역할을 재정의하거나 다른 변수 사용 고려
                            )
                        }
                        lastEmergencyNotificationTimestamps[serverUserIdStr] = currentTime

                        Log.d(TAG, "긴급 알림 UI 상태 업데이트: Nick='${scannedDevice.nickname}'")


                        // <<--- 진동 발생시키기 ---START>>
                        vibratePhone() // 진동 함수 호출
                        // <<--- 진동 발생시키기 ---END>>

                        // 5초 후 긴급 시각 효과 자동 해제
                        viewModelScope.launch {
                            delay(15000L) // 예: 15초 후 자동 해제 (EMERGENCY_VISUAL_DURATION_MILLIS와는 별개)
                            // 타이머 만료 시, 현재 알림이 여전히 동일한 사용자의 것인지 확인 후 해제
                            if (_uiState.value.rescueRequestReceived && _uiState.value.rescueRequesterNickname == scannedDevice.nickname) {
                                dismissRescueAlert() // rescueRequestReceived = false, rescueRequesterNickname = null 로 설정
                            }
                        }
                    } else {
                        Log.d(TAG, "수신된 긴급 패킷의 신호가 약함 (RSSI: ${scannedDevice.rssi}). 알림 표시 안 함.")
                    }
                }else {
                        Log.d(
                            TAG,
                            "수신: '${scannedDevice.nickname}'로부터 온 긴급 구조 요청이지만, 쿨다운(${EMERGENCY_NOTIFICATION_COOLDOWN_MS / 60000}분) 시간 내이므로 UI 알림 무시."
                        )
                    }
                }

            // 계산된 시각적 홉수 = 상대방 광고 홉수 + 1
            val calculatedVisualDepth = scannedDevice.advertisedOwnDepth + 1
            val advertisedDeviceDepth = scannedDevice.advertisedOwnDepth

            // 앵커 기기 발견 (0인 기기를 발견)
            if (advertisedDeviceDepth == 0) {
                foundAnchorDevice = true
                // 앵커 기기와 1홉 거리
                shortestPathToRootViaNeighbor = 1
            } 
            // 다른 계산 로직은 그대로 유지
            else if (calculatedVisualDepth < NeighborDiscoveryConstants.MAX_TRACKABLE_DEPTH) {
                if (shortestPathToRootViaNeighbor == -1 || calculatedVisualDepth < shortestPathToRootViaNeighbor) {
                    shortestPathToRootViaNeighbor = calculatedVisualDepth
                }
            }

            val smoothedRssi = SignalStrengthManager.getSmoothedRssi(bleAddress, scannedDevice.rssi)
            val signalLevel = when {
                smoothedRssi >= -60 -> 3
                smoothedRssi >= -75 -> 2
                else -> 1
            }

            val serverUserIdStr = scannedDevice.serverUserId.toString()
            
            // 프로필 이미지 결정: 프로필 이미지 값이 0보다 크면 해당 값을 사용
            val profileImageNumber = if (scannedDevice.serverUserId == myServerUserId) {
                _uiState.value.userProfileImageNumber // 내 프로필이면 저장된 이미지 번호 사용
            } else if (scannedDevice.profileImageNumber > 0) {
                scannedDevice.profileImageNumber // BLE에서 받은 프로필 이미지 번호 사용
            } else {
                0 // 프로필 이미지 번호가 없으면 0으로 설정 (UI에서 서버ID 기반 랜덤 결정)
            }
            
            // 마지막 UI 업데이트 시간 확인 
            val lastUIUpdate = lastUIUpdatedTimestamps[serverUserIdStr] ?: 0L
            val isNewDevice = lastUIUpdate == 0L
            
            // 새 디바이스, 업데이트 간격이 지났거나, 긴급 상태일 때만 전체 정보 업데이트
            if (isNewDevice || 
                currentTime - lastUIUpdate >= UI_UPDATE_INTERVAL_SHORT_MS || 
                scannedDevice.isEmergency == 1.toByte()) {
                
                // UI 업데이트 시간 갱신
                lastUIUpdatedTimestamps[serverUserIdStr] = currentTime
                
                // 각도는 처음 생성 시에만 랜덤하게 생성하고, 이후에는 유지
                val angle = if (updatedNearbyMap.containsKey(serverUserIdStr)) {
                    updatedNearbyMap[serverUserIdStr]!!.angle // 기존 각도 유지
                } else if (!isNewDevice && _uiState.value.nearbyPeople.any { it.serverUserIdString == serverUserIdStr }) {
                    // 업데이트 맵에는 없지만 UI 상태에 있는 경우 (기존 디바이스)
                    _uiState.value.nearbyPeople.find { it.serverUserIdString == serverUserIdStr }!!.angle
                } else {
                    // 완전히 새로운 디바이스면 새 각도 생성
                    (abs(scannedDevice.serverUserId.hashCode()) % 360).toFloat()
                }
                
                // 업데이트된 정보로 NearbyPerson 객체 생성
                val newPerson = NearbyPerson(
                    serverUserIdString = serverUserIdStr,
                    nickname = scannedDevice.nickname.ifBlank { "익명_${serverUserIdStr.takeLast(4)}" },
                    calculatedVisualDepth = calculatedVisualDepth,
                    advertisedDeviceDepth = advertisedDeviceDepth,
                    rssi = smoothedRssi,
                    signalLevel = signalLevel,
                    angle = angle,
                    lastSeenTimestamp = scannedDevice.lastSeen,
                    profileImageNumber = profileImageNumber
                )
                updatedNearbyMap[serverUserIdStr] = newPerson
            } else {
                // 업데이트 간격이 지나지 않은 경우, 기존 정보를 유지하되 RSSI, signalLevel, lastSeen만 업데이트
                val existingPerson = _uiState.value.nearbyPeople.find { it.serverUserIdString == serverUserIdStr }
                if (existingPerson != null) {
                    updatedNearbyMap[serverUserIdStr] = existingPerson.copy(
                        rssi = smoothedRssi,
                        signalLevel = signalLevel,
                        lastSeenTimestamp = scannedDevice.lastSeen,
                        // 프로필 이미지도 최신 정보로 항상 업데이트
                        profileImageNumber = profileImageNumber
                    )
                }
            }
        }

        // 앵커 기기를 발견했거나 최단 경로가 현재 내 홉수보다 짧을 때만 홉수 업데이트
        if (foundAnchorDevice || (shortestPathToRootViaNeighbor != -1 && shortestPathToRootViaNeighbor < myCurrentAdvertisedDepth)) {
            // 현재 내 홉수와 다를 때만 업데이트
            if (myCurrentAdvertisedDepth != shortestPathToRootViaNeighbor) {
                Log.i(TAG, "내 광고 Depth 변경: $myCurrentAdvertisedDepth -> $shortestPathToRootViaNeighbor")
                myCurrentAdvertisedDepth = shortestPathToRootViaNeighbor
                _uiState.update { it.copy(currentSelfAdvertisedDepth = myCurrentAdvertisedDepth) }
                startOrUpdateAdvertising()
            }
        } else if (currentScannedDevices.isEmpty() && myCurrentAdvertisedDepth != 0) {
            // 주변 기기가 없으면 내 홉수를 0으로 리셋
            Log.i(TAG, "주변 탐색된 기기 없음. 내 광고 Depth를 0으로 리셋.")
            myCurrentAdvertisedDepth = 0
            _uiState.update { it.copy(currentSelfAdvertisedDepth = myCurrentAdvertisedDepth) }
            startOrUpdateAdvertising()
        }

        // 오래된 디바이스 제거 (UI 목록에서)
        lastUIUpdatedTimestamps.entries.removeIf { (id, timestamp) ->
            val shouldRemove = currentTime - timestamp > NeighborDiscoveryConstants.DEVICE_EXPIRATION_MS
            if (shouldRemove) {
                Log.d(TAG, "오래된 디바이스 UI 정보 제거: ID=$id, 마지막 업데이트: ${(currentTime - timestamp) / 1000}초 전")
            }
            shouldRemove
        }

        val newNearbyList = updatedNearbyMap.values.toList().sortedWith(
            compareBy<NearbyPerson> { it.calculatedVisualDepth }
                .thenByDescending { it.signalLevel }
                .thenByDescending { it.rssi }
        )
        
        // 항상 업데이트하도록 변경하여 UI 반영 문제 해결
        _uiState.update { it.copy(
            nearbyPeople = newNearbyList,
            showListButton = newNearbyList.isNotEmpty()
        ) }
    }

    fun dismissEmergencyAlert() {
        _uiState.update {
            it.copy(
                showEmergencyAlert = false,
                // emergencyAlertNickname = null, // 닉네임은 유지해도 되고, null로 바꿔도 됨 (UI 정책에 따라)
                isEmergencyVisualEffectActive = false // 시각 효과는 확실히 중단
            )
        }
        // 필요하다면 중복 알림 방지용 타임스탬프도 초기화
        // _uiState.update { it.copy(currentEmergencyPacketTimestamp = 0L) }
        Log.d(TAG, "긴급 알림 UI 해제됨.")
    }


    fun togglePersonListModal() {
        _uiState.update { it.copy(showPersonListModal = !it.showPersonListModal) }
    }

    fun onCallClick(serverUserIdString: String, navController: NavController, callViewModel: CallViewModel) {
        val person = _uiState.value.nearbyPeople.find { it.serverUserIdString == serverUserIdString }
        if (person != null) {
            // 목록 모달 닫기
            togglePersonListModal()

            // 통화 화면으로 이동 전에 타겟 설정
            callViewModel.setTargetPerson(person)

            // 아웃고잉 화면으로 이동
            navController.navigate(AppDestinations.OUTGOING_CALL_ROUTE.replace("{receiverId}", serverUserIdString))
        } else {
            Log.w(TAG, "클릭된 사용자 ID($serverUserIdString)를 현재 목록에서 찾을 수 없습니다.")
        }
    }

    fun onPersonClick(serverUserIdString: String) {
        val person = _uiState.value.nearbyPeople.find { it.serverUserIdString == serverUserIdString }
        if (person != null) {
            // 프로필 모달을 표시하도록 상태 업데이트
            _uiState.update { it.copy(
                showProfileModal = true,
                selectedProfilePerson = person
            )}
        }
    }

    fun onProfileModalDismiss() {
        _uiState.update { it.copy(
            showProfileModal = false,
            selectedProfilePerson = null
        )}
    }

    fun onCallFromProfileModal() {
        val serverUserIdString = _uiState.value.selectedProfilePerson?.serverUserIdString
        if (serverUserIdString != null) {
            // 프로필 모달 닫기
            _uiState.update { it.copy(
                showProfileModal = false,
                selectedProfilePerson = null,
                navigateToProfileServerUserIdString = serverUserIdString
            )}
        }
    }

    fun onProfileScreenNavigated() = _uiState.update { it.copy(navigateToProfileServerUserIdString = null) }
    fun setDisplayDepthLevel(level: Int) {
        _uiState.update { it.copy(displayDepthLevel = level.coerceIn(1, NeighborDiscoveryConstants.DISPLAY_DEPTH_LEVELS.lastOrNull() ?: NeighborDiscoveryConstants.MAX_TRACKABLE_DEPTH)) }
    }
    fun clearErrorMessage() = _uiState.update { it.copy(errorMessage = null) }

    fun activateAI() {
        val now = System.currentTimeMillis()
        if (now - lastAiActivationTime < 2000) {
            return
        }
        lastAiActivationTime = now
        _aiActive.value = true
    }

    fun deactivateAI() {
        _aiActive.value = false
    }

    fun startScanAutomatically() {
        val currentState = _uiState.value
        Log.d(TAG, "startScanAutomatically 호출됨. 현재 스캔 상태: ${currentState.isScanningActive}, BLE 준비 상태: ${currentState.isBleReady}")
        
        if (!currentState.isScanningActive && currentState.isBleReady) {
            // BLE가 준비되어 있고 스캔 중이 아니면 스캔 시작
            _uiState.update { it.copy(isScanningActive = true, buttonText = "탐색 중...", errorMessage = null) }
            startBleOperationsInternal()
        } else if (!currentState.isBleReady) {
            // BLE가 준비되지 않으면 실패 로그 출력
            Log.w(TAG, "자동 스캔 실패: BLE 준비 안됨. (권한=${currentState.blePermissionsGranted}, BT=${currentState.isBluetoothEnabled}, 로그인=${myServerUserId != -1L})")
        }
    }

    // 화면의 "BLE 상태 확인" 버튼 등에서 명시적으로 호출될 때 사용
    fun checkBluetoothStateAndPermissionsExplicitly() {
        Log.d(TAG, "명시적 BLE 상태 및 권한 확인 요청")
        activityRef?.get()?.let {
            updateBluetoothStateAndPermissions(it)
        } ?: checkBleReadyState() // Activity 없으면 상태만 재확인
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel onCleared: 모든 BLE 리소스 해제")
        stopBleOperationsInternal()
        NeighborScanner.release()
        NeighborAdvertiser.release()
        activityRef?.clear()
        activityRef = null // WeakReference이므로 명시적 null 처리는 불필요할 수 있으나, 확실히 함
    }

    // 블루투스 상태와 권한을 확인하고 UI 상태 업데이트 (Activity Context 필요)
    fun updateBluetoothStateAndPermissions(activity: Activity) {
        Log.d(TAG, "updateBluetoothStateAndPermissions 호출됨")
        // 1. 블루투스 활성화 상태 확인
        val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val isBluetoothOn = bluetoothManager?.adapter?.isEnabled == true
        Log.i(TAG, "블루투스 상태: ${if (isBluetoothOn) "활성화" else "비활성화"}")
        updateBluetoothState(isBluetoothOn)
        
        // 2. BLE 권한 상태 확인
        val hasBluetoothScanPermission = ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        val hasBluetoothAdvertisePermission = ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        val hasBluetoothConnectPermission = ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        
        // 3. 위치 권한 확인 (Android 12 미만에서 필요)
        val needLocationPermission = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S
        val hasLocationPermission = if (needLocationPermission) {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true
        
        // 4. 결과 기록
        val blePermissionsGranted = hasBluetoothScanPermission && hasBluetoothAdvertisePermission && hasBluetoothConnectPermission && (hasLocationPermission || !needLocationPermission)
        Log.i(TAG, "BLE 권한 전체: ${if (blePermissionsGranted) "승인됨" else "거부됨"}")
        Log.i(TAG, "  BLUETOOTH_SCAN: ${if (hasBluetoothScanPermission) "승인됨" else "거부됨"}")
        Log.i(TAG, "  BLUETOOTH_ADVERTISE: ${if (hasBluetoothAdvertisePermission) "승인됨" else "거부됨"}")
        Log.i(TAG, "  BLUETOOTH_CONNECT: ${if (hasBluetoothConnectPermission) "승인됨" else "거부됨"}")
        if (needLocationPermission) {
            Log.i(TAG, "  ACCESS_FINE_LOCATION: ${if (hasLocationPermission) "승인됨" else "거부됨"} (Android 12 미만)")
        }
        
        updateBlePermissionStatus(blePermissionsGranted)
    }

    fun dismissRescueAlert() {
        _uiState.update {
            it.copy(
                rescueRequestReceived = false,
                rescueRequesterNickname = null
            )
        }
        Log.d(TAG, "Rescue alert dismissed.")
    }
    
    /**
     * 블루투스가 꺼져 있을 때 사용자에게 블루투스를 켜도록 요청하는 메서드
     * MainActivity에서 호출하여 블루투스 활성화 다이얼로그를 표시합니다.
     */
    fun requestBluetoothEnable() {
        Log.i(TAG, "블루투스 활성화 요청")
        // 실제 요청은 MainActivity에서 처리됩니다.
        // 이 메서드는 MainScreen에서 사용자가 스캔 버튼을 눌렀을 때 BLE가 준비되지 않았다면 호출됩니다.
    }
}
