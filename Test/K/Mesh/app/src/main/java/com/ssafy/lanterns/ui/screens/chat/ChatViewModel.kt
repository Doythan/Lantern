package com.ssafy.lanterns.ui.screens.chat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.lanterns.data.repository.MessageRepository
import com.ssafy.lanterns.data.source.ble.mesh.ChatMessage
import com.ssafy.lanterns.data.source.ble.mesh.NearbyNode
import com.ssafy.lanterns.service.ConnectionState
import com.ssafy.lanterns.service.MeshNetworkService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MutableStateFlow에 update 확장 함수 추가
 */
private fun <T> MutableStateFlow<T>.updateState(transform: (T) -> T) {
    value = transform(value)
}

/**
 * 채팅 화면 UI 상태
 */
data class ChatUiState(
    val permissionGranted: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val isAdvertising: Boolean = false,
    val nearbyDevices: List<NearbyNode> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val deviceNickname: String = "",
    val currentChatPartner: NearbyNode? = null, // 1:1 채팅 중인 파트너
    val isNetworkActive: Boolean = false,
    val showNicknameDialog: Boolean = false,
    val errorMessage: String? = null,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED
)

/**
 * 채팅 ViewModel
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    application: Application,
    private val messageRepository: MessageRepository
) : AndroidViewModel(application) {
    
    private val TAG = "ChatViewModel"
    
    // UI 상태
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    // 메쉬 네트워크 서비스 바인딩
    private var meshNetworkService: MeshNetworkService? = null
    private var isBound = false
    
    // 애플리케이션 컨텍스트
    private val context: Context
        get() = getApplication()
    
    // 블루투스 관련 컴포넌트
    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager?.adapter
    }
    
    // 서비스 연결 객체
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected")
            val binder = service as MeshNetworkService.MeshNetworkBinder
            meshNetworkService = binder.getService()
            isBound = true
            
            // 서비스로부터 상태 수집
            collectServiceState()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            meshNetworkService = null
            isBound = false
            
            // 연결 상태 업데이트
            _uiState.updateState { it.copy(
                connectionState = ConnectionState.DISCONNECTED,
                isNetworkActive = false
            )}
        }
    }
    
    // 저장된 모든 메시지
    val savedMessages = messageRepository.getChatMessages()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // 그룹 채팅 메시지
    val groupMessages = messageRepository.getGroupMessages()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    init {
        Log.d(TAG, "Initializing ChatViewModel")
        
        // 블루투스 상태 확인
        updateBluetoothState()
        
        // 저장된 닉네임 로드
        loadDeviceNickname()
        
        // 서비스 바인딩
        bindToService()
    }
    
    /**
     * 서비스에 바인딩
     */
    private fun bindToService() {
        val serviceIntent = Intent(context, MeshNetworkService::class.java)
        context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    /**
     * 서비스 상태 수집
     */
    private fun collectServiceState() {
        meshNetworkService?.let { service ->
            // 연결 상태 수집
            viewModelScope.launch {
                service.connectionState.collectLatest { state ->
                    Log.d(TAG, "Connection state changed: $state")
                    _uiState.updateState { it.copy(
                        connectionState = state,
                        isNetworkActive = state == ConnectionState.CONNECTED
                    )}
                }
            }
            
            // 주변 노드 목록 수집
            viewModelScope.launch {
                service.nearbyNodes.collectLatest { nodes ->
                    Log.d(TAG, "Nearby nodes updated: ${nodes.size}")
                    _uiState.updateState { it.copy(nearbyDevices = nodes) }
                }
            }
            
            // 저장된 메시지 수집 - 현재 채팅 상대에 따라 적절한 메시지 로드
            loadMessages()
        }
    }
    
    /**
     * 메시지 로드
     * 현재 채팅 상대에 따라 적절한 메시지 로드
     */
    private fun loadMessages() {
        viewModelScope.launch {
            val currentPartner = _uiState.value.currentChatPartner
            
            if (currentPartner != null && currentPartner.address != null) {
                // 1:1 채팅 메시지 로드
                messageRepository.getDirectMessages(currentPartner.address)
                    .collectLatest { messages ->
                        _uiState.updateState { it.copy(messages = messages) }
                    }
            } else {
                // 그룹 채팅 메시지 로드
                messageRepository.getGroupMessages()
                    .collectLatest { messages ->
                        _uiState.updateState { it.copy(messages = messages) }
                    }
            }
        }
    }
    
    /**
     * 디바이스 닉네임 설정
     */
    fun updateDeviceNickname(nickname: String) {
        _uiState.updateState { it.copy(deviceNickname = nickname) }
        
        // 닉네임 저장
        viewModelScope.launch {
            saveDeviceNickname(nickname)
        }
        
        // 권한 체크 후 서비스에 닉네임 업데이트
        val serviceIntent = Intent(context, MeshNetworkService::class.java).apply {
            putExtra(MeshNetworkService.EXTRA_COMMAND, MeshNetworkService.COMMAND_UPDATE_NICKNAME)
            putExtra(MeshNetworkService.EXTRA_NICKNAME, nickname)
        }
        context.startService(serviceIntent)
    }
    
    /**
     * 닉네임 다이얼로그 표시/숨김
     */
    fun showNicknameDialog(show: Boolean) {
        _uiState.updateState { it.copy(showNicknameDialog = show) }
    }
    
    /**
     * 닉네임 저장
     */
    private suspend fun saveDeviceNickname(nickname: String) {
        val prefs = context.getSharedPreferences("lantern_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("device_nickname", nickname).apply()
    }
    
    /**
     * 저장된 닉네임 로드
     */
    private fun loadDeviceNickname() {
        val prefs = context.getSharedPreferences("lantern_prefs", Context.MODE_PRIVATE)
        val savedNickname = prefs.getString("device_nickname", null) 
        
        if (savedNickname != null) {
            _uiState.updateState { it.copy(deviceNickname = savedNickname) }
            return
        }
        
        // 블루투스 권한이 있는지 확인
        val hasBluetoothPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
        
        // 권한이 있을 때만 블루투스 어댑터 이름 접근
        val deviceName = if (hasBluetoothPermission) {
            val adapter = bluetoothAdapter
            if (adapter != null && adapter.isEnabled) {
                adapter.name
            } else {
                null
            }
        } else {
            null
        }
        
        val nickname = deviceName ?: "Unknown"
        _uiState.updateState { it.copy(deviceNickname = nickname) }
    }
    
    /**
     * 메시 네트워크 시작
     * 채팅을 위한 BLE 메시 네트워크 활성화
     */
    fun startMeshNetwork() {
        if (!uiState.value.bluetoothEnabled) {
            _uiState.updateState { it.copy(errorMessage = "블루투스가 활성화되지 않았습니다.") }
            return
        }
        
        // 모든 필요한 권한 확인
        val permissionsGranted = hasRequiredPermissions()
        
        if (permissionsGranted) {
            val adapter = bluetoothAdapter
            val deviceAddress = adapter?.address

            val serviceIntent = Intent(context, MeshNetworkService::class.java).apply {
                putExtra(MeshNetworkService.EXTRA_COMMAND, MeshNetworkService.COMMAND_START)
                putExtra(MeshNetworkService.EXTRA_NICKNAME, uiState.value.deviceNickname)
                putExtra(MeshNetworkService.EXTRA_DEVICE_ADDRESS, deviceAddress)
            }
            context.startService(serviceIntent)
        } else {
            _uiState.updateState { it.copy(errorMessage = "블루투스 권한이 없습니다. 앱 설정에서 권한을 활성화해주세요.") }
        }
    }
    
    /**
     * 모든 필요한 블루투스 권한이 있는지 확인
     */
    private fun hasRequiredPermissions(): Boolean {
        // API 레벨에 따라 필요한 권한 목록 설정
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 메시 네트워크 중지
     */
    fun stopMeshNetwork() {
        val serviceIntent = Intent(context, MeshNetworkService::class.java).apply {
            putExtra(MeshNetworkService.EXTRA_COMMAND, MeshNetworkService.COMMAND_STOP)
        }
        context.startService(serviceIntent)
    }
    
    /**
     * 그룹 메시지 전송
     * 모든 주변 기기에 메시지 전송
     */
    fun sendGroupMessage(content: String) {
        if (content.isEmpty()) return
        
        // 메시지 크기 검증
        if (content.length > 500) {
            _uiState.updateState { it.copy(errorMessage = "메시지가 너무 깁니다. 500자 이내로 작성해주세요.") }
            return
        }
        
        if (meshNetworkService == null) {
            _uiState.updateState { it.copy(errorMessage = "서비스에 연결되어 있지 않습니다.") }
            return
        }
        
        val success = try {
            meshNetworkService?.sendGroupMessage(content) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "메시지 전송 중 오류 발생", e)
            false
        }
        
        if (!success) {
            _uiState.updateState { it.copy(errorMessage = "메시지 전송에 실패했습니다.") }
        }
    }
    
    /**
     * 1:1 메시지 전송
     * 특정 기기에 메시지 전송
     */
    fun sendDirectMessage(content: String) {
        if (content.isEmpty()) return
        
        // 메시지 크기 검증
        if (content.length > 500) {
            _uiState.updateState { it.copy(errorMessage = "메시지가 너무 깁니다. 500자 이내로 작성해주세요.") }
            return
        }
        
        if (meshNetworkService == null) {
            _uiState.updateState { it.copy(errorMessage = "서비스에 연결되어 있지 않습니다.") }
            return
        }
        
        val partner = uiState.value.currentChatPartner
        if (partner?.address != null) {
            val success = try {
                meshNetworkService?.sendDirectMessage(partner.address, content) ?: false
            } catch (e: Exception) {
                Log.e(TAG, "메시지 전송 중 오류 발생", e)
                false
            }
            
            if (!success) {
                _uiState.updateState { it.copy(errorMessage = "메시지 전송에 실패했습니다.") }
            }
        } else {
            _uiState.updateState { 
                it.copy(errorMessage = "채팅 상대가 선택되지 않았거나 메시 주소가 없습니다.") 
            }
        }
    }
    
    /**
     * 1:1 채팅 시작
     */
    fun startDirectChat(node: NearbyNode) {
        _uiState.updateState { it.copy(currentChatPartner = node) }
        // 채팅 상대가 변경되었으므로 메시지 다시 로드
        if (node.address != null) {
            loadDirectMessages(node.address)
        }
    }
    
    /**
     * 그룹 채팅으로 돌아가기
     */
    fun returnToGroupChat() {
        _uiState.updateState { it.copy(currentChatPartner = null) }
        // 그룹 채팅 메시지 로드
        viewModelScope.launch {
            messageRepository.getGroupMessages()
                .collectLatest { messages ->
                    _uiState.updateState { it.copy(messages = messages) }
                }
        }
    }
    
    /**
     * 권한 상태 업데이트
     */
    fun updatePermissionStatus(granted: Boolean) {
        _uiState.updateState { it.copy(permissionGranted = granted) }
        
        if (granted && !uiState.value.isNetworkActive) {
            startMeshNetwork()
        }
    }
    
    /**
     * 블루투스 상태 업데이트
     */
    fun updateBluetoothState() {
        val adapter = bluetoothAdapter
        val isEnabled = adapter?.isEnabled == true
        _uiState.updateState { it.copy(bluetoothEnabled = isEnabled) }
    }
    
    /**
     * 에러 메시지 표시
     */
    fun showError(message: String) {
        _uiState.updateState { it.copy(errorMessage = message) }
    }
    
    /**
     * 에러 메시지 제거
     */
    fun clearError() {
        _uiState.updateState { it.copy(errorMessage = null) }
    }
    
    /**
     * 리소스 해제
     */
    override fun onCleared() {
        super.onCleared()
        
        // 서비스 바인딩 해제
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
    }
    
    /**
     * 1:1 채팅 메시지 로드
     */
    fun loadDirectMessages(recipientAddress: Short) {
        viewModelScope.launch {
            messageRepository.getDirectMessages(recipientAddress)
                .collectLatest { messages ->
                    _uiState.updateState { it.copy(messages = messages) }
                }
        }
    }

    /**
     * 블루투스 설정 화면으로 이동
     */
    fun openBluetoothSettings() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    
    /**
     * 앱 설정 화면으로 이동
     */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    
    /**
     * 필요한 권한 목록 가져오기
     */
    fun getRequiredPermissions(): List<String> {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }
} 