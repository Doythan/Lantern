package com.ssafy.lanterns.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.lanterns.ui.screens.main.components.NearbyPerson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * 메인 화면의 상태를 관리하는 ViewModel
 */
data class MainScreenState(
    val isScanning: Boolean = false,
    val nearbyPeople: List<NearbyPerson> = emptyList(),
    val showPersonListModal: Boolean = false,
    val buttonText: String = "시작하기",
    val statusText: String = "주변을 탐색하고 있습니다",
    val subTextVisible: Boolean = false,
    val showListButton: Boolean = false,
    
    // BLE 관련 상태 추가
    // BLE 광고 및 스캔 기능 활성화 상태
    val isBleServiceActive: Boolean = false,
    
    // 프로필 화면 이동을 위한 userId 상태
    val navigateToProfile: String? = null,

    // AI 관련 상태 추가
    val showOnDeviceAI: Boolean = false
)

@HiltViewModel
class MainViewModel @Inject constructor(
    // TODO: BLE 관련 의존성 주입
    // private val bleServiceManager: BleServiceManager
) : ViewModel() {
    
    // UI 상태 관리
    private val _uiState = MutableStateFlow(MainScreenState())
    val uiState: StateFlow<MainScreenState> = _uiState.asStateFlow()
    
    // AI 상태 관리
    private val _aiActive = MutableStateFlow(false)
    val aiActive: StateFlow<Boolean> = _aiActive.asStateFlow()
    
    // 스캔 작업을 처리하는 Job
    private var scanningJob: Job? = null
    
    /**
     * AI 화면을 활성화합니다.
     */
    fun activateAI() {
        _aiActive.value = true
    }

    /**
     * AI 화면을 비활성화합니다.
     */
    fun deactivateAI() {
        _aiActive.value = false
    }
    
    /**
     * 스캔 상태를 토글합니다.
     */
    fun toggleScan() {
        if (_uiState.value.isScanning) {
            stopScanning()
        } else {
            startScanning()
        }
    }
    
    /**
     * 스캔을 시작합니다.
     * 이 함수에서 BLE 광고 및 스캔을 시작하는 로직을 추가해야 합니다.
     */
    fun startScanning() {
        // 이미 스캔 중이라면 중복 실행 방지
        if (_uiState.value.isScanning) return
        
        _uiState.update { state ->
            state.copy(
                isScanning = true,
                buttonText = "탐색 중...",
                subTextVisible = true,
                isBleServiceActive = true, // BLE 서비스 활성화 상태로 설정
                nearbyPeople = emptyList() // 시작할 때는 빈 리스트로 초기화
            )
        }
        
        // =====================================================
        // TODO: BLE 광고 및 스캔 시작 로직 추가
        // 여기서 BLE 광고와 스캔을 시작하는 코드를 호출해야 합니다.
        // 아래는 BleServiceManager를 사용하는 예시 코드입니다:
        // 
        // try {
        //     bleServiceManager.startBleService()
        //     // BLE 서비스 시작 성공 처리
        // } catch (e: Exception) {
        //     // BLE 서비스 시작 실패 처리
        //     _uiState.update { it.copy(
        //         statusText = "BLE 서비스 시작 실패: ${e.message}",
        //     )}
        // }
        // =====================================================
        
        // 미리 정의된 다양한 거리의 사용자들을 생성
        val predefinedPeople = listOf(
            // 0-100m 사이의 근거리 사용자들 (2명)
            NearbyPerson(
                id = 1,
                distance = 42f,
                angle = 45f,
                signalStrength = 0.85f
            ),
            NearbyPerson(
                id = 2,
                distance = 87f,
                angle = 135f,
                signalStrength = 0.75f
            ),
            
            // 100-300m 사이의 중거리 사용자들 (2명)
            NearbyPerson(
                id = 3,
                distance = 154f,
                angle = 210f,
                signalStrength = 0.55f
            ),
            NearbyPerson(
                id = 4,
                distance = 267f,
                angle = 315f,
                signalStrength = 0.45f
            ),
            
            // 300m 이상의 원거리 사용자들 (2명)
            NearbyPerson(
                id = 5,
                distance = 345f,
                angle = 90f,
                signalStrength = 0.35f
            ),
            NearbyPerson(
                id = 6,
                distance = 478f,
                angle = 270f,
                signalStrength = 0.25f
            )
        )
        
        // 시뮬레이션 작업 시작 - 사람을 하나씩 발견하는 효과
        scanningJob = viewModelScope.launch {
            val discoveredPeople = mutableListOf<NearbyPerson>()
            
            // 사람들을 발견하는 시뮬레이션 (가까운 사람부터 발견)
            for (person in predefinedPeople.sortedBy { it.distance }) {
                delay(800) // 각 사람을 발견하는데 약간의 딜레이
                
                discoveredPeople.add(person)
                
                // UI 상태 업데이트
                _uiState.update { state ->
                    state.copy(
                        nearbyPeople = discoveredPeople.toList(),
                        statusText = "주변에 ${discoveredPeople.size}명이 있습니다",
                        showListButton = discoveredPeople.isNotEmpty()
                    )
                }
            }
            
            // 모든 사람을 발견한 후 주기적인 상태 업데이트 시뮬레이션
            while (true) {
                delay(5000) // 5초마다 업데이트 확인
                
                // 진행 중인 스캔 상태 유지
                _uiState.update { state ->
                    state.copy(
                        statusText = "주변에 ${discoveredPeople.size}명이 있습니다"
                    )
                }
            }
        }
    }
    
    /**
     * 스캔을 중지합니다.
     * 이 함수에서 BLE 광고 및 스캔을 중지하는 로직을 추가해야 합니다.
     */
    fun stopScanning() {
        scanningJob?.cancel()
        scanningJob = null
        
        _uiState.update { state ->
            state.copy(
                isScanning = false,
                buttonText = "시작하기",
                subTextVisible = false,
                nearbyPeople = emptyList(),
                statusText = "주변을 탐색하고 있습니다",
                showListButton = false,
                isBleServiceActive = false // BLE 서비스 비활성화 상태로 설정
            )
        }
        
        // =====================================================
        // TODO: BLE 광고 및 스캔 중지 로직 추가
        // 여기서 BLE 광고와 스캔을 중지하는 코드를 호출해야 합니다.
        // 아래는 BleServiceManager를 사용하는 예시 코드입니다:
        // 
        // try {
        //     bleServiceManager.stopBleService()
        //     // BLE 서비스 중지 성공 처리
        // } catch (e: Exception) {
        //     // BLE 서비스 중지 실패 처리
        //     // 실패해도 UI는 이미 중지 상태로 변경됨
        // }
        // =====================================================
    }
    
    /**
     * 사용자 목록 모달 표시 상태를 토글합니다.
     */
    fun togglePersonListModal() {
        _uiState.update { state ->
            state.copy(showPersonListModal = !state.showPersonListModal)
        }
    }
    
    /**
     * 화면이 다시 표시될 때 스캔 상태를 복원합니다.
     */
    fun restoreScanningStateIfNeeded() {
        if (_uiState.value.isScanning) {
            // 스캔 중인 상태라면 스캔을 다시 시작
            // 기존 Job이 취소되었을 수 있으므로 새로 시작
            scanningJob?.cancel()
            startScanning()
            
            // =====================================================
            // TODO: BLE 서비스 상태 확인 및 복원 로직
            // 앱이 백그라운드에서 포그라운드로 돌아올 때 BLE 서비스 상태를 확인하고
            // 필요하다면 다시 시작합니다.
            // 
            // boolean isServiceRunning = bleServiceManager.isServiceRunning();
            // if (_uiState.value.isBleServiceActive && !isServiceRunning) {
            //     bleServiceManager.startBleService();
            // }
            // =====================================================
        }
    }
    
    /**
     * 사용자 목록에서 사용자를 클릭했을 때 호출됩니다.
     */
    fun onPersonClick(userId: String) {
        _uiState.update { it.copy(navigateToProfile = userId) }
    }

    /**
     * 프로필 화면으로 네비게이션 후 호출되어 상태를 초기화합니다.
     */
    fun onProfileScreenNavigated() {
        _uiState.update { it.copy(navigateToProfile = null) }
    }
    
    /**
     * BLE 관련 권한 상태를 업데이트합니다.
     * BLE 기능을 사용하기 위해 필요한 권한이 부여되었는지 확인하고 처리합니다.
     */
    @Suppress("UNUSED_PARAMETER") // BLE 로직 구현 시 사용 예정
    fun updateBlePermissionStatus(granted: Boolean) {
        // =====================================================
        // TODO: [BLE] BLE 권한 상태 업데이트 로직
        // BLE 권한이 부여되었는지 확인하고 상태를 업데이트합니다.
        // 
        // if (granted) {
        //     // 권한이 부여된 경우, BLE 서비스가 활성화되어야 한다면 시작
        //     if (_uiState.value.isBleServiceActive) {
        //         bleServiceManager.startBleService()
        //     }
        // } else {
        //     // 권한이 거부된 경우, 필요한 처리
        //     if (_uiState.value.isScanning) {
        //         // 권한이 없는데 스캔 중이라면 스캔 중지
        //         stopScanning()
        //     }
        //     _uiState.update { it.copy(
        //         statusText = "BLE 기능을 사용하려면 권한이 필요합니다"
        //     )}
        // }
        // =====================================================
    }
    
    /**
     * 블루투스 상태를 업데이트합니다.
     * 블루투스가 활성화되었는지 확인하고 처리합니다.
     */
    @Suppress("UNUSED_PARAMETER") // BLE 로직 구현 시 사용 예정
    fun updateBluetoothState(enabled: Boolean) {
        // =====================================================
        // TODO: 블루투스 상태 업데이트 로직
        // 블루투스가 활성화되었는지 확인하고 상태를 업데이트합니다.
        // 
        // if (enabled) {
        //     // 블루투스가 활성화된 경우, BLE 서비스가 활성화되어야 한다면 시작
        //     if (_uiState.value.isBleServiceActive) {
        //         bleServiceManager.startBleService()
        //     }
        // } else {
        //     // 블루투스가 비활성화된 경우, 필요한 처리
        //     if (_uiState.value.isScanning) {
        //         // 블루투스가 꺼져 있는데 스캔 중이라면 스캔 중지
        //         stopScanning()
        //     }
        //     _uiState.update { it.copy(
        //         statusText = "BLE 기능을 사용하려면 블루투스를 활성화해야 합니다"
        //     )}
        // }
        // =====================================================
    }
    
    // ViewModel이 제거될 때 자원 정리
    override fun onCleared() {
        super.onCleared()
        scanningJob?.cancel()
        
        // =====================================================
        // TODO: BLE 서비스 정리 로직
        // ViewModel이 제거될 때 BLE 서비스를 정리합니다.
        // 
        // if (_uiState.value.isBleServiceActive) {
        //     bleServiceManager.stopBleService()
        // }
        // =====================================================
    }
} 