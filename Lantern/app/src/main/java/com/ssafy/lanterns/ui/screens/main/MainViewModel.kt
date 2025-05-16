package com.ssafy.lanterns.ui.screens.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.lanterns.ui.screens.main.components.NearbyPerson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * 메인 화면의 상태를 관리하는 ViewModel
 */
data class MainScreenState(
    val isScanning: Boolean = true,              // 항상 스캔 중
    val nearbyPeople: List<NearbyPerson> = emptyList(),
    val showPersonListModal: Boolean = false,
    val buttonText: String = "탐색 중...",        // 항상 "탐색 중..."
    val subTextVisible: Boolean = true,          // 항상 표시
    val showListButton: Boolean = false,

    // BLE 상태
    val isBleServiceActive: Boolean = true,      // 항상 활성화

    // 프로필 이동용 userId
    val navigateToProfile: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    // TODO: BLE 의존성 주입
) : ViewModel() {

    /* -------------------------------------------------- *
     * constants
     * -------------------------------------------------- */
    companion object {
        private const val TAG = "MainViewModel"
        private const val AI_ACTIVATION_DEBOUNCE_MS = 2_000L
    }

    /* -------------------------------------------------- *
     * UI 상태
     * -------------------------------------------------- */
    private val _uiState = MutableStateFlow(MainScreenState())
    val uiState: StateFlow<MainScreenState> = _uiState.asStateFlow()

    /* -------------------------------------------------- *
     * AI 다이얼로그 상태
     * -------------------------------------------------- */
    private val _aiActive = MutableStateFlow(false)
    val aiActive: StateFlow<Boolean> = _aiActive.asStateFlow()

    private var lastAiActivationTime = 0L

    /* -------------------------------------------------- *
     * BLE 스캔 job
     * -------------------------------------------------- */
    private var scanningJob: Job? = null

    /* -------------------------------------------------- *
     * init
     * -------------------------------------------------- */
    init {
        // ViewModel 생성 시 자동 스캔 시작
        startScanning()
    }

    /* ==================================================
     *  AI 다이얼로그 제어
     * ================================================== */

    /** "헤이 랜턴" 감지 시 호출 */
    fun activateAI() {
        val now = System.currentTimeMillis()
        if (now - lastAiActivationTime < AI_ACTIVATION_DEBOUNCE_MS) {
            Log.d(TAG, "activateAI() 무시 (debounce)")
            return
        }
        lastAiActivationTime = now
        _aiActive.value = true
        Log.d(TAG, "activateAI() → _aiActive = true")
    }

    /** 다이얼로그 닫힐 때 호출 */
    fun deactivateAI() {
        _aiActive.value = false
        Log.d(TAG, "deactivateAI() → _aiActive = false")
    }

    /* ==================================================
     *  BLE 스캔 로직 (모킹)
     * ================================================== */

    /** 실제 BLE 스캔 시작 */
    fun startScanning() {
        // 이미 스캔 중이면 무시
        if (_uiState.value.isScanning && scanningJob != null) return

        _uiState.update { it.copy(
            isScanning = true,
            buttonText = "탐색 중...",
            subTextVisible = true,
            isBleServiceActive = true,
            nearbyPeople = emptyList(),
            showListButton = true
        )}

        // 모킹용 사람 리스트
        val predefinedPeople = listOf(
            NearbyPerson(id = 1, distance = 42f, angle = 45f, signalStrength = 0.85f),   // 0~100 m
            NearbyPerson(id = 2, distance = 87f, angle = 135f, signalStrength = 0.75f),
            NearbyPerson(id = 3, distance = 154f, angle = 210f, signalStrength = 0.55f),   // 100~300 m
            NearbyPerson(id = 4, distance = 267f, angle = 315f, signalStrength = 0.45f),
            NearbyPerson(id = 5, distance = 345f, angle = 90f, signalStrength = 0.35f),   // 300 m+
            NearbyPerson(id = 6, distance = 478f, angle = 270f, signalStrength = 0.25f)
        )

        scanningJob = viewModelScope.launch {
            val discovered = mutableListOf<NearbyPerson>()

            // 가까운 사람부터 하나씩 "발견"
            for (person in predefinedPeople.sortedBy { it.distance }) {
                delay(800)
                discovered.add(person)
                _uiState.update { it.copy(nearbyPeople = discovered.toList()) }
            }

            // 이후 5초마다 상태 유지 갱신
            while (true) {
                delay(5_000)
                _uiState.update { it.copy(buttonText = "탐색 중...") }
            }
        }
    }

    /* 스캔 토글 (현재는 항상 스캔 중이므로 미사용) */
    fun toggleScan() { /* no-op */ }

    /* ==================================================
     *  UI 상호작용 헬퍼
     * ================================================== */

    fun togglePersonListModal() {
        if (_uiState.value.nearbyPeople.isNotEmpty()) {
            _uiState.update { it.copy(showPersonListModal = !it.showPersonListModal) }
        }
    }

    fun restoreScanningStateIfNeeded() {
        if (scanningJob == null || !_uiState.value.isScanning) {
            scanningJob?.cancel()
            startScanning()
        }
    }

    fun onPersonClick(userId: String) {
        _uiState.update { it.copy(navigateToProfile = userId) }
    }

    fun onProfileScreenNavigated() {
        _uiState.update { it.copy(navigateToProfile = null) }
    }

    /* ==================================================
     *  BLE 권한/상태 처리 (TODO)
     * ================================================== */

    @Suppress("UNUSED_PARAMETER")
    fun updateBlePermissionStatus(granted: Boolean) {
        // TODO: BLE 권한 처리 로직
    }

    @Suppress("UNUSED_PARAMETER")
    fun updateBluetoothState(enabled: Boolean) {
        // TODO: 블루투스 on/off 처리 로직
    }

    /* -------------------------------------------------- *
     * ViewModel 소멸 시 자원 정리
     * -------------------------------------------------- */
    override fun onCleared() {
        super.onCleared()
        scanningJob?.cancel()
        // TODO: BLE 서비스 정리
    }
}
