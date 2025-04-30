package com.example.blemodule.ui.viewmodel.main

import androidx.lifecycle.* // ViewModel, LiveData, ViewModelProvider 등 임포트
import com.example.blemodule.data.model.BleDevice
import com.example.blemodule.data.model.Message
import com.example.blemodule.data.repository.BleRepository // Repository 임포트
import com.example.blemodule.data.state.AdvertisingState
import com.example.blemodule.data.state.ScanState
import com.example.blemodule.util.Constants
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.launchIn // launchIn 임포트
import kotlinx.coroutines.flow.onEach // onEach 임포트
import kotlinx.coroutines.launch // launch 임포트

/**
 * MainActivity 와 관련된 UI 상태 및 비즈니스 로직을 처리하는 ViewModel 입니다.
 * BleRepository 를 통해 BLE 데이터 및 상태 변화를 관찰하고 UI 에 필요한 형태로 가공합니다.
 *
 * @property repository BleRepository 인스턴스 (ViewModelFactory 를 통해 주입받음)
 */
class MainViewModel(private val repository: BleRepository) : ViewModel() {

    // --- LiveData / StateFlow for UI State ---

    // 서비스 활성 상태 (읽기 전용 LiveData)
    private val _isServiceActive = MutableLiveData<Boolean>()
    val isServiceActive: LiveData<Boolean> = _isServiceActive

    // 스캔된 기기 목록 (읽기 전용 LiveData)
    private val _scannedDevices = MutableLiveData<List<BleDevice>>()
    val scannedDevices: LiveData<List<BleDevice>> = _scannedDevices

    // 연결된 기기 목록 (읽기 전용 LiveData)
    private val _connectedDevices = MutableLiveData<List<BleDevice>>()
    val connectedDevices: LiveData<List<BleDevice>> = _connectedDevices

    // 네트워크 상의 알려진 기기 ID 목록 (읽기 전용 LiveData)
    private val _knownDevices = MutableLiveData<Set<String>>()
    val knownDevices: LiveData<Set<String>> = _knownDevices

    // 수신된 App 메시지 (단발성 이벤트를 위해 SingleLiveEvent 또는 SharedFlow 고려 가능)
    private val _receivedMessage = MutableLiveData<Message.AppMessage>()
    val receivedMessage: LiveData<Message.AppMessage> = _receivedMessage

    // 로그 메시지 목록 (RecyclerView 등에 표시하기 위한 리스트 형태)
    private val _logMessages = MutableLiveData<List<String>>(emptyList())
    val logMessages: LiveData<List<String>> = _logMessages

    // 스캔 상태 (읽기 전용 LiveData)
    private val _scanState = MutableLiveData<ScanState>()
    val scanState: LiveData<ScanState> = _scanState

    // 광고 상태 (읽기 전용 LiveData)
    private val _advertisingState = MutableLiveData<AdvertisingState>()
    val advertisingState: LiveData<AdvertisingState> = _advertisingState

    // 현재 입력된 내 기기 ID (UI 와 양방향 바인딩 또는 상태 저장용)
    val myDeviceId = MutableLiveData<String>("")

    init {
        // ViewModel 생성 시 Repository 의 Flow 들을 구독 시작
        observeRepositoryFlows()
    }

    /**
     * BleRepository 에서 제공하는 Flow 들을 관찰하고 LiveData 를 업데이트합니다.
     */
    private fun observeRepositoryFlows() {
        // viewModelScope: ViewModel 의 생명주기에 맞춰 관리되는 CoroutineScope
        repository.isServiceActive
            .onEach { _isServiceActive.postValue(it) } // 백그라운드 스레드에서 호출될 수 있으므로 postValue 사용
            .launchIn(viewModelScope)

        repository.scannedDevices
            .onEach { deviceMap -> _scannedDevices.postValue(deviceMap.values.toList()) }
            .launchIn(viewModelScope)

        repository.connectedDevices
            .onEach { deviceMap -> _connectedDevices.postValue(deviceMap.values.toList()) }
            .launchIn(viewModelScope)

        repository.knownDevicesFlow
            .onEach { _knownDevices.postValue(it) }
            .launchIn(viewModelScope)

        repository.receivedAppMessages
            .buffer()
            .onEach { _receivedMessage.postValue(it) } // SingleLiveEvent 고려
            .launchIn(viewModelScope)

        repository.logMessages
            .onEach { newLog ->
                // 이전 로그 리스트에 새 로그 추가 (최대 개수 제한 등 추가 가능)
                val currentLogs = _logMessages.value ?: emptyList()
                _logMessages.postValue(currentLogs + newLog)
            }
            .launchIn(viewModelScope)

        repository.scanState
            .onEach { _scanState.postValue(it) }
            .launchIn(viewModelScope)

        repository.advertisingState
            .onEach { _advertisingState.postValue(it) }
            .launchIn(viewModelScope)
    }

    // --- UI Actions ---

    /**
     * 내 기기 ID 를 설정하고 서비스를 시작/중지하는 로직과 연결 (Activity에서 호출)
     * @param id 입력된 내 기기 ID
     * @param startService 서비스 시작 함수 (Activity 로부터 전달받음)
     * @param stopService 서비스 중지 함수 (Activity 로부터 전달받음)
     * @return ID 유효성 여부
     */
    fun validateAndSetId(
        id: String,
        startService: (String) -> Unit,
        stopService: () -> Unit
    ): Boolean {
        val trimmed = id.trim()
        // 1) 길이 검사
        if (trimmed.isEmpty() || trimmed.length > Constants.MAX_NICKNAME_LENGTH) return false

        // 2) Repository에 설정 (중복·길이 중복 검사 로직 포함)
        val ok = repository.setMyDeviceId(trimmed)
        if (!ok) return false        // 중복일 때 false

        myDeviceId.value = trimmed   // UI 바인딩용 LiveData
        startService(trimmed)        // 성공하면 서비스 시작
        return true
    }

    /**
     * Ping 메시지 전송 요청 (Activity 에서 호출)
     * @param targetId 대상 기기 ID
     */
    fun sendChat(targetId: String?, text: String) {
        val me = myDeviceId.value ?: return
        val msgText = text.trim()
        if (msgText.isEmpty()) return

        val target = targetId?.takeIf { it.isNotBlank() } ?: Constants.BROADCAST_ID
        val msg = Message.createAppMessage(target, me, msgText)
        repository.sendMessage(msg)          // 모든 연결로 릴레이
    }

    fun connectTo(address: String) = repository.connectGatt(address)

    /**
     * 로그 추가 (필요시 ViewModel 에서 직접 로그 추가 가능)
     */
    private fun log(message: String) {
        // Repository 의 로그 Flow 를 사용하므로 여기서는 직접 추가 불필요할 수 있음
        // 필요하다면 _logMessages 업데이트 로직 추가
        println("MainViewModel Log: $message") // 디버그용 콘솔 출력
    }

}

/**
 * MainViewModel 인스턴스를 생성하기 위한 ViewModelProvider.Factory 입니다.
 * BleRepository 의존성을 생성자를 통해 주입받습니다.
 */
class MainViewModelFactory(private val repository: BleRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // 요청된 ViewModel 클래스가 MainViewModel 과 호환되는지 확인
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            // MainViewModel 인스턴스를 생성하며 repository 주입
            @Suppress("UNCHECKED_CAST") // 캐스팅 경고 무시
            return MainViewModel(repository) as T
        }
        // 요청된 클래스가 MainViewModel 이 아니면 예외 발생
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}