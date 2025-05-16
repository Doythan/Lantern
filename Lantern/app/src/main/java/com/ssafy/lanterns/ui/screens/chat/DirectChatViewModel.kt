package com.ssafy.lanterns.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.lanterns.data.model.Messages
import com.ssafy.lanterns.data.model.User
import com.ssafy.lanterns.data.repository.ChatRoomDao
import com.ssafy.lanterns.data.repository.MessagesDao
import com.ssafy.lanterns.data.repository.UserDao
import com.ssafy.lanterns.data.repository.UserRepository
import com.ssafy.lanterns.ui.navigation.AppDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * 1대1 채팅 메시지 데이터 클래스
 */
data class DirectChatUiState(
    val isLoading: Boolean = false,
    val messages: List<DirectMessage> = emptyList(),
    val participant: User? = null,
    val chatRoomId: Long? = null,
    val errorMessage: String? = null,
    val connectionStatus: String = "연결 안됨", // 연결 상태 (BLE 연결 상태를 표시)
    val isConnecting: Boolean = false,
    val isLoadingMore: Boolean = false, // 더 많은 메시지 로드 중인지 여부
    val hasMoreMessages: Boolean = true, // 더 로드할 메시지가 있는지 여부
    val signalStrength: Int = 0, // 신호 세기 (0-100)
    
    // BLE 관련 상태 추가
    val requiredPermissionsGranted: Boolean = false, // BLE 권한 부여 여부
    val isBluetoothEnabled: Boolean = false, // 블루투스 활성화 여부
    val isScanning: Boolean = false, // BLE 스캔 중 여부
    val isAdvertising: Boolean = false, // BLE 광고 중 여부
    val scannedDevices: Map<String, String> = emptyMap() // 스캔된 BLE 기기 목록 (주소 -> 이름)
)

/**
 * 1대1 채팅 화면을 위한 ViewModel
 */
@HiltViewModel
class DirectChatViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val userDao: UserDao,
    private val chatRoomDao: ChatRoomDao,
    private val messagesDao: MessagesDao,
    savedStateHandle: SavedStateHandle
    // BLE 관련 의존성 주입 필요
    // private val advertiserManager: AdvertiserManager,
    // private val gattServerManager: GattServerManager,
    // private val gattClientManager: GattClientManager,
    // private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(DirectChatUiState(isLoading = true))
    val uiState: StateFlow<DirectChatUiState> = _uiState.asStateFlow()

    private var currentUser: User? = null
    private var userId: String? = null
    private var oldestMessageDate: LocalDateTime? = null
    private val pageSize = 20 // 한 번에 로드할 메시지 수

    // BLE 관련 필드 추가
    // private val bluetoothAdapter: BluetoothAdapter? by lazy {
    //     val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    //     bluetoothManager?.adapter
    // }
    
    // private val scannerManager: ScannerManager by lazy {
    //     ScannerManager(application, ::handleScanResult)
    // }

    init {
        // 네비게이션 파라미터에서 사용자 ID 가져오기
        userId = savedStateHandle.get<String>(AppDestinations.DIRECT_CHAT_ARG_USER_ID)
        
        // 테스트 사용자 및 데이터 로드
        viewModelScope.launch {
            try {
                // 현재 사용자 정보 확인
                currentUser = userRepository.getCurrentUser()
                
                // 사용자 정보가 없으면 오류 메시지 표시
                if (currentUser == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "로그인된 사용자 정보가 없습니다. 로그인을 진행해주세요."
                        )
                    }
                    return@launch
                }

                // 채팅 상대방 정보 가져오기
                val participantId = userId?.toLongOrNull() ?: 2L // 기본값 설정
                val participant = userDao.getUserById(participantId)
                
                if (participant == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "사용자 정보를 찾을 수 없습니다."
                        )
                    }
                    return@launch
                }

                // 채팅방 정보 가져오기
                val chatRooms = chatRoomDao.getChatRoomsByParticipantId(participantId)
                
                var chatRoomId: Long? = null
                if (chatRooms.isNotEmpty()) {
                    // 이미 존재하는 채팅방 사용
                    chatRoomId = chatRooms.first().chatRoomId
                } else {
                    // 새 채팅방 생성
                    chatRoomId = chatRoomDao.InsertChatRoom(
                        com.ssafy.lanterns.data.model.ChatRoom(
                            chatRoomId = System.currentTimeMillis(),
                            updatedAt = LocalDateTime.now(),
                            participantId = participantId
                        )
                    )
                }

                // UI 상태 업데이트
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        participant = participant,
                        chatRoomId = chatRoomId
                    )
                }

                // 채팅 메시지 가져오기
                loadInitialMessages()

                // BLE 연결 시뮬레이션 (나중에 실제 BLE 연결 로직으로 대체)
                simulateConnection()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "초기 데이터 로드 중 오류: ${e.message}"
                    )
                }
            }
        }
        
        // BLE 초기화 로직 추가
        // setupBleCallbacks()
        // checkInitialBluetoothState()
    }

    /**
     * 오류 메시지를 지웁니다.
     */
    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 초기 데이터 로드
     */
    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                // 로그인 상태 확인 (이미 currentUser가 있음)
                if (currentUser == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "로그인 정보를 찾을 수 없습니다."
                        )
                    }
                    return@launch
                }

                // 채팅 상대방 정보 가져오기
                val participantId = userId?.toLongOrNull() ?: 2L // 기본값 설정
                val participant = userDao.getUserById(participantId)
                
                if (participant == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "사용자 정보를 찾을 수 없습니다."
                        )
                    }
                    return@launch
                }

                // 채팅방 정보 가져오기
                val chatRooms = chatRoomDao.getChatRoomsByParticipantId(participantId)
                
                var chatRoomId: Long? = null
                if (chatRooms.isNotEmpty()) {
                    // 이미 존재하는 채팅방 사용
                    chatRoomId = chatRooms.first().chatRoomId
                } else {
                    // 새 채팅방 생성
                    chatRoomId = chatRoomDao.InsertChatRoom(
                        com.ssafy.lanterns.data.model.ChatRoom(
                            chatRoomId = System.currentTimeMillis(),
                            updatedAt = LocalDateTime.now(),
                            participantId = participantId
                        )
                    )
                }

                // UI 상태 업데이트
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        participant = participant,
                        chatRoomId = chatRoomId
                    )
                }

                // 채팅 메시지 가져오기
                loadInitialMessages()

                // BLE 연결 시뮬레이션 (나중에 실제 BLE 연결 로직으로 대체)
                simulateConnection()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "초기 데이터 로드 중 오류: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 초기 채팅 메시지 로드
     */
    private fun loadInitialMessages() {
        viewModelScope.launch {
            try {
                val chatRoomId = _uiState.value.chatRoomId ?: return@launch
                
                // 로딩 상태 업데이트
                _uiState.update { it.copy(isLoading = true) }
                
                // 실제 데이터베이스에서 메시지 로드
                var messages = messagesDao.getMessages(chatRoomId, pageSize, 0)
                
                // 메시지가 없으면 테스트용 가짜 데이터 생성 및 저장
                if (messages.isEmpty()) {
                    val testMessages = createTestMessages(chatRoomId)
                    
                    // 가짜 메시지 데이터베이스에 저장
                    testMessages.forEach { message ->
                        messagesDao.insertMessage(message)
                    }
                    
                    // 저장된 메시지 다시 로드
                    messages = messagesDao.getMessages(chatRoomId, pageSize, 0)
                }
                
                if (messages.isNotEmpty()) {
                    // 가장 오래된 메시지 날짜 저장
                    oldestMessageDate = messages.lastOrNull()?.date
                }
                
                // 메시지를 UI에 맞게 변환
                val directMessages = messages.map { message ->
                    convertMessageToDirectMessage(message)
                }

                // UI 상태 업데이트
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        messages = directMessages,
                        hasMoreMessages = messages.size >= pageSize
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "메시지 로드 중 오류: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 테스트용 가짜 메시지 생성
     */
    private fun createTestMessages(chatRoomId: Long): List<Messages> {
        val currentUserId = currentUser?.userId ?: 1L
        val participantId = userId?.toLongOrNull() ?: 2L
        val currentTime = LocalDateTime.now()
        
        // 테스트 메시지 내용
        val conversationTemplate = listOf(
            Pair(currentUserId, "안녕하세요! 통화 기능을 테스트해 볼까요?"),
            Pair(participantId, "네, 좋아요! 어떻게 해야 하나요?"),
            Pair(currentUserId, "위쪽에 통화 버튼을 클릭하면 됩니다."),
            Pair(participantId, "확인해볼게요. 지금 해볼까요?"),
            Pair(currentUserId, "네, 준비됐으면 통화 버튼을 눌러주세요."),
            Pair(participantId, "잠시만요, 무슨 내용으로 얘기할까요?"),
            Pair(currentUserId, "테스트니까 간단하게 인사만 해도 될 것 같아요."),
            Pair(participantId, "알겠습니다. 통화 버튼을 눌러보겠습니다."),
            Pair(currentUserId, "그리고 나중에 한번 더 테스트도 해봐요."),
            Pair(participantId, "네, 좋아요! 블루투스 연결도 테스트해보면 좋을 것 같아요."),
            Pair(currentUserId, "맞아요. BLE 스캔 및 광고 기능도 곧 추가될 예정입니다."),
            Pair(participantId, "기대되네요! 연결 시 어떤 방식으로 동작하는지 궁금해요."),
            Pair(currentUserId, "직접 연결된 디바이스 간 P2P 방식으로 통신할 예정입니다."),
            Pair(participantId, "아하, 그럼 서버 없이도 통신할 수 있겠네요?"),
            Pair(currentUserId, "네, 맞습니다. 서버를 거치지 않고 직접 연결됩니다."),
            Pair(participantId, "멋지네요! 이제 통화 버튼을 눌러볼까요?"),
            Pair(currentUserId, "네, 통화해봅시다!")
        )
        
        // 메시지 객체 생성
        return conversationTemplate.mapIndexed { index, (senderId, text) ->
            val messageTime = currentTime.minusMinutes((conversationTemplate.size - index).toLong())
            Messages(
                messageId = System.currentTimeMillis() + index,
                chatRoomId = chatRoomId,
                userId = senderId,
                text = text,
                date = messageTime
            )
        }
    }

    /**
     * 메시지 객체를 UI에 표시할 DirectMessage로 변환
     */
    private fun convertMessageToDirectMessage(message: Messages): DirectMessage {
        val isMe = message.userId == currentUser?.userId
        return DirectMessage(
            id = message.messageId.toInt(),
            sender = if (isMe) "나" else _uiState.value.participant?.nickname ?: "상대방",
            text = message.text,
            time = formatTime(message.date),
            isMe = isMe,
            senderProfileId = if (isMe) null else _uiState.value.participant?.userId?.toInt()
        )
    }

    /**
     * 더 오래된 메시지 불러오기 (무한 스크롤)
     */
    fun loadMoreMessages() {
        // 이미 로딩 중이거나, 더 불러올 메시지가 없는 경우 중단
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMoreMessages) {
            return
        }
        
        viewModelScope.launch {
            try {
                val chatRoomId = _uiState.value.chatRoomId ?: return@launch
                val oldestDate = oldestMessageDate ?: return@launch
                
                // 로딩 상태 업데이트
                _uiState.update { it.copy(isLoadingMore = true) }
                
                // 현재 가지고 있는 가장 오래된 메시지보다 이전의 메시지 가져오기
                val olderMessages = messagesDao.getMessagesBefore(
                    chatRoomId = chatRoomId,
                    beforeDate = oldestDate,
                    limit = pageSize
                )
                
                if (olderMessages.isNotEmpty()) {
                    // 가장 오래된 메시지 날짜 업데이트
                    oldestMessageDate = olderMessages.lastOrNull()?.date
                    
                    // 메시지를 UI에 맞게 변환
                    val olderDirectMessages = olderMessages.map { message ->
                        convertMessageToDirectMessage(message)
                    }
                    
                    // 현재 메시지 목록에 새로 가져온 메시지 추가
                    val updatedMessages = _uiState.value.messages + olderDirectMessages
                    
                    // UI 상태 업데이트
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            messages = updatedMessages,
                            hasMoreMessages = olderMessages.size >= pageSize
                        )
                    }
                } else {
                    // 더 이상 메시지가 없음
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            hasMoreMessages = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        errorMessage = "추가 메시지 로드 중 오류: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 새 메시지 전송
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            try {
                val chatRoomId = _uiState.value.chatRoomId ?: return@launch
                val currentUserId = currentUser?.userId ?: return@launch

                // 메시지 생성 및 저장
                val message = Messages(
                    messageId = System.currentTimeMillis(),
                    userId = currentUserId,
                    chatRoomId = chatRoomId,
                    text = text,
                    date = LocalDateTime.now()
                )

                messagesDao.insertMessage(message)

                // 채팅방 업데이트 시간 갱신
                chatRoomDao.updateChatRoomUpdateAt(chatRoomId)

                // 새 메시지 UI에 추가
                val newDirectMessage = convertMessageToDirectMessage(message)
                val updatedMessages = listOf(newDirectMessage) + _uiState.value.messages
                
                _uiState.update {
                    it.copy(messages = updatedMessages)
                }

                // (실제로는 BLE 통신으로 메시지 전송 필요)
                // TODO: BLE 메시지 전송 구현
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "메시지 전송 중 오류: ${e.message}")
                }
            }
        }
    }

    /**
     * 수신된 메시지 처리 (BLE 통신으로부터 수신)
     */
    fun receiveMessage(text: String) {
        viewModelScope.launch {
            try {
                val chatRoomId = _uiState.value.chatRoomId ?: return@launch
                val participantId = _uiState.value.participant?.userId ?: return@launch

                // 메시지 생성 및 저장
                val message = Messages(
                    messageId = System.currentTimeMillis(),
                    userId = participantId, // 상대방 ID
                    chatRoomId = chatRoomId,
                    text = text,
                    date = LocalDateTime.now()
                )

                messagesDao.insertMessage(message)

                // 채팅방 업데이트 시간 갱신
                chatRoomDao.updateChatRoomUpdateAt(chatRoomId)

                // 새 메시지 UI에 추가
                val newDirectMessage = convertMessageToDirectMessage(message)
                val updatedMessages = listOf(newDirectMessage) + _uiState.value.messages
                
                _uiState.update {
                    it.copy(messages = updatedMessages)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "메시지 수신 중 오류: ${e.message}")
                }
            }
        }
    }

    /**
     * BLE 연결 시뮬레이션 (추후 실제 BLE 구현으로 대체될 예정)
     */
    private fun simulateConnection() {
        viewModelScope.launch {
            try {
                _uiState.update { 
                    it.copy(
                        isConnecting = true,
                        connectionStatus = "연결 중..."
                    )
                }
                
                // 연결 시도 시뮬레이션
                delay(2000)
                
                // 연결 성공 시뮬레이션
                _uiState.update { 
                    it.copy(
                        isConnecting = false,
                        connectionStatus = "연결됨",
                        signalStrength = Random.nextInt(50, 90) // 초기 신호 세기
                    )
                }
                
                // 주기적으로 신호 세기 업데이트
                simulateSignalStrength()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        connectionStatus = "연결 실패",
                        errorMessage = "BLE 연결 중 오류: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * 신호 세기 시뮬레이션
     */
    private fun simulateSignalStrength() {
        viewModelScope.launch {
            while (true) {
                delay(5000) // 5초마다 신호 세기 변경
                
                // 현재 신호 세기에 약간의 변동 적용
                val currentStrength = _uiState.value.signalStrength
                val fluctuation = Random.nextInt(-20, 20)
                val newStrength = (currentStrength + fluctuation).coerceIn(10, 95)
                
                _uiState.update { 
                    it.copy(signalStrength = newStrength)
                }
            }
        }
    }

    /**
     * 채팅방 나가기 (연결 해제)
     */
    fun exitChat() {
        viewModelScope.launch {
            // 연결 해제 상태로 변경
            _uiState.update { it.copy(connectionStatus = "연결 해제 중...") }
            delay(500) // 연결 해제 지연 시간 시뮬레이션
            // 상태 초기화 (실제로는 필요 없을 수 있음 - 화면을 나가면 ViewModel은 폐기됨)
            _uiState.update { it.copy(connectionStatus = "연결 안됨") }
        }
    }

    /**
     * 시간 형식 변환
     */
    private fun formatTime(dateTime: LocalDateTime): String {
        return dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    // --- BLE 관련 로직 추가 (주석 처리) --- //
    
    /**
     * BLE 콜백 설정
     */
    // private fun setupBleCallbacks() {
    //     // GattClientManager 콜백 설정
    //     gattClientManager.onConnectionStateChange = ::handleClientConnectionStateChange
    //     gattClientManager.onMessageReceived = ::handleMessageReceived
    //
    //     // GattServerManager 콜백 설정
    //     gattServerManager.onConnectionStateChange = ::handleServerConnectionStateChange
    //     gattServerManager.onClientSubscribed = { /* 클라이언트 구독 시 처리 */ }
    //     gattServerManager.onClientUnsubscribed = { /* 클라이언트 구독 해제 시 처리 */ }
    // }
    
    /**
     * 블루투스 초기 상태 확인
     */
    // private fun checkInitialBluetoothState() {
    //     _uiState.update { it.copy(isBluetoothEnabled = bluetoothAdapter?.isEnabled == true) }
    // }
    
    /**
     * BLE 권한 상태 업데이트
     */
    // fun updatePermissionStatus(granted: Boolean) {
    //     _uiState.update { it.copy(requiredPermissionsGranted = granted) }
    //     if (granted && _uiState.value.isBluetoothEnabled) {
    //         startBleOperations()
    //     }
    // }
    
    /**
     * 블루투스 활성화 상태 업데이트
     */
    // fun updateBluetoothState(enabled: Boolean) {
    //     _uiState.update { it.copy(isBluetoothEnabled = enabled) }
    //     if (enabled && _uiState.value.requiredPermissionsGranted) {
    //         startBleOperations()
    //     } else if (!enabled) {
    //         stopBleOperations()
    //         _uiState.update { it.copy(errorMessage = "블루투스를 활성화해주세요.") }
    //     }
    // }
    
    /**
     * BLE 작업 시작
     */
    // fun startBleOperations() {
    //     if (!_uiState.value.requiredPermissionsGranted || !_uiState.value.isBluetoothEnabled) {
    //         return
    //     }
    //     
    //     viewModelScope.launch {
    //         try {
    //             gattServerManager.openGattServer()
    //             advertiserManager.startAdvertising()
    //             scannerManager.startScanning(scanCallback) // ViewModel의 콜백 전달
    //             _uiState.update { it.copy(isScanning = true, isAdvertising = true, errorMessage = null) }
    //         } catch (e: Exception) {
    //             _uiState.update { it.copy(errorMessage = "BLE 시작 오류: ${e.message}") }
    //         }
    //     }
    // }
    
    /**
     * BLE 작업 중지
     */
    // fun stopBleOperations() {
    //     viewModelScope.launch {
    //         try {
    //             scannerManager.stopScanning()
    //             advertiserManager.stopAdvertising()
    //             gattServerManager.closeGattServer()
    //             gattClientManager.disconnectAll()
    //             _uiState.update {
    //                 it.copy(
    //                     isScanning = false,
    //                     isAdvertising = false,
    //                     scannedDevices = emptyMap(),
    //                     connectionStatus = "연결 안됨"
    //                 )
    //             }
    //         } catch (e: Exception) {
    //             _uiState.update { it.copy(errorMessage = "BLE 종료 오류: ${e.message}") }
    //         }
    //     }
    // }
    
    /**
     * BLE를 통한 메시지 전송
     */
    // fun sendMessageViaBle(message: String) {
    //     viewModelScope.launch {
    //         try {
    //             gattServerManager.broadcastMessage(message)
    //         } catch (e: Exception) {
    //             _uiState.update { it.copy(errorMessage = "메시지 전송 오류: ${e.message}") }
    //         }
    //     }
    // }
    
    /**
     * 스캔 결과 처리
     */
    // private fun handleScanResult(result: ScanResult) {
    //     if (!_uiState.value.requiredPermissionsGranted) return
    //     
    //     val device = result.device
    //     val deviceName = device.name ?: "알 수 없는 기기"
    //     val deviceAddress = device.address
    //     
    //     if (!_uiState.value.scannedDevices.containsKey(deviceAddress)) {
    //         _uiState.update { state ->
    //             state.copy(scannedDevices = state.scannedDevices + (deviceAddress to deviceName))
    //         }
    //     }
    //     
    //     // 추가 로직: 특정 조건에 따라 자동 연결을 시도할 수 있음
    // }
    
    /**
     * 특정 기기에 연결
     */
    // fun connectToDevice(deviceAddress: String) {
    //     if (!_uiState.value.requiredPermissionsGranted || !_uiState.value.isBluetoothEnabled) {
    //         _uiState.update { it.copy(errorMessage = "연결하려면 권한 및 블루투스 활성화가 필요합니다.") }
    //         return
    //     }
    //     
    //     val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
    //     if (device != null) {
    //         _uiState.update { it.copy(isConnecting = true, errorMessage = null) }
    //         gattClientManager.connectToDevice(device)
    //     } else {
    //         _uiState.update { it.copy(errorMessage = "기기를 찾을 수 없습니다.") }
    //     }
    // }
    
    /**
     * ViewModel 정리
     */
    override fun onCleared() {
        super.onCleared()
        // BLE 작업 정리
        // stopBleOperations()
    }
} 