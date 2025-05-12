package com.ssafy.lanterns.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.lanterns.data.model.ChatRoom
import com.ssafy.lanterns.data.model.Messages
import com.ssafy.lanterns.data.model.User
import com.ssafy.lanterns.data.repository.ChatRoomDao
import com.ssafy.lanterns.data.repository.MessagesDao
import com.ssafy.lanterns.data.repository.UserDao
import com.ssafy.lanterns.data.repository.UserRepository
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

/**
 * 채팅 리스트 UI 상태를 위한 데이터 클래스
 */
data class ChatListUiState(
    val isLoading: Boolean = false,
    val chatList: List<ChatItem> = emptyList(),
    val nearbyUsers: List<NearbyUser> = emptyList(),
    val errorMessage: String? = null
)

/**
 * 채팅 리스트 화면을 위한 ViewModel
 */
@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val userDao: UserDao,
    private val chatRoomDao: ChatRoomDao,
    private val messagesDao: MessagesDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState(isLoading = true))
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    private var currentUser: User? = null

    init {
        loadCurrentUser()
    }

    /**
     * 현재 사용자 정보 로드 및 채팅방 목록 조회
     */
    private fun loadCurrentUser() {
        viewModelScope.launch {
            try {
                // 현재 사용자 정보 확인
                currentUser = userRepository.getCurrentUser()
                
                // 사용자 정보가 없으면 테스트 사용자 생성
                if (currentUser == null) {
                    currentUser = userRepository.ensureTestUser()
                    
                    if (currentUser == null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "테스트 사용자 생성 실패"
                            )
                        }
                        return@launch
                    }
                }
                
                // 채팅방 및 주변 사용자 로드
                loadChatRooms()
                generateNearbyUsers() // 주변 사용자 더미 데이터 생성 (실제로는 BLE 스캐닝으로 대체)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "사용자 정보 로드 중 오류: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 채팅방 목록 불러오기
     */
    private fun loadChatRooms() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                // 현재 사용자의 채팅방 목록 조회
                val userId = currentUser?.userId ?: return@launch
                val chatRooms = chatRoomDao.getChatRoomsByParticipantId(userId)
                
                // 채팅방별 마지막 메시지 및 상대방 정보 조회
                val chatItems = mutableListOf<ChatItem>()
                
                // 기존 더미 데이터로 테스트를 위한 임시 로직 (실제 구현에서는 제거)
                if (chatRooms.isEmpty()) {
                    insertDummyChatData()
                    return@launch loadChatRooms() // 재귀 호출로 다시 로드
                }
                
                for (chatRoom in chatRooms) {
                    val participantId = chatRoom.participantId
                    val participant = userDao.getUserById(participantId)
                    
                    // 해당 채팅방의 최신 메시지 가져오기
                    val messages = messagesDao.getMessages(chatRoom.chatRoomId, 1, 0)
                    val lastMessage = messages.firstOrNull()
                    
                    // 임의의 거리 설정 (실제로는 BLE를 통해 거리 계산)
                    val distance = Random.nextFloat() * 300
                    
                    // 채팅 아이템 생성
                    if (participant != null) {
                        chatItems.add(
                            ChatItem(
                                id = participantId.toInt(),  // 상대방 ID로 설정
                                name = participant.nickname,
                                lastMessage = lastMessage?.text ?: "대화를 시작해보세요",
                                time = formatDateTime(lastMessage?.date ?: LocalDateTime.now()),
                                unread = false, // 읽음 상태 관리 기능 추가 필요
                                distance = distance
                            )
                        )
                    }
                }
                
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        chatList = chatItems,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "채팅방 목록 로드 중 오류: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * 테스트를 위한 더미 채팅 데이터 생성 (샘플)
     */
    private suspend fun insertDummyChatData() {
        try {
            val currentUserId = currentUser?.userId ?: return
            
            // 먼저 DirectChatViewModel에서 생성한 테스트 사용자 확인 (userId=2)
            val testPartner = userDao.getUserById(2L)
            
            // 더미 사용자 목록 준비
            val dummyUsers = mutableListOf<User>()
            
            // 테스트 파트너가 없으면 테스트 사용자 추가
            if (testPartner == null) {
                dummyUsers.add(User(2L, "테스트 파트너", "test_device_002"))
            }
            
            // 기존 더미 사용자 추가
            dummyUsers.addAll(
                listOf(
                    User(100L, "도경원", "device_100"),
                    User(101L, "귀요미", "device_101"),
                    User(102L, "백성욱", "device_102"),
                    User(103L, "박수민", "device_103"),
                    User(104L, "천세욱1", "device_104"),
                    User(105L, "천세욱2", "device_105")
                )
            )
            
            // 사용자 저장
            dummyUsers.forEach { user ->
                userDao.insertUser(user)
            }
            
            // 채팅방 생성
            val allUsers = if (testPartner != null) {
                dummyUsers + testPartner
            } else {
                dummyUsers
            }
            
            allUsers.forEach { user ->
                // 기존 채팅방 확인
                val existingRooms = chatRoomDao.getChatRoomsByParticipantId(user.userId)
                
                if (existingRooms.isEmpty()) {
                    // 새 채팅방 생성
                    val chatRoomId = chatRoomDao.InsertChatRoom(
                        ChatRoom(
                            chatRoomId = System.currentTimeMillis() + user.userId,
                            updatedAt = LocalDateTime.now(),
                            participantId = user.userId
                        )
                    )
                    
                    // 테스트 파트너일 경우 더 많은 샘플 메시지 추가
                    if (user.userId == 2L) {
                        val conversationTemplate = listOf(
                            Pair(currentUserId, "안녕하세요! 통화 기능을 테스트해 볼까요?"),
                            Pair(user.userId, "네, 좋아요! 어떻게 해야 하나요?"),
                            Pair(currentUserId, "채팅방에 들어가서 위쪽에 통화 버튼을 클릭하면 됩니다."),
                            Pair(user.userId, "확인해볼게요. 지금 해볼까요?")
                        )
                        
                        val currentTime = LocalDateTime.now()
                        
                        // 메시지 객체 생성 및 저장
                        conversationTemplate.mapIndexed { index, (senderId, text) ->
                            val messageTime = currentTime.minusMinutes((conversationTemplate.size - index).toLong())
                            val message = Messages(
                                messageId = System.currentTimeMillis() + index,
                                chatRoomId = chatRoomId,
                                userId = senderId,
                                text = text,
                                date = messageTime
                            )
                            messagesDao.insertMessage(message)
                        }
                    } else {
                        // 일반 더미 메시지 생성
                        val dummyMessages = listOf(
                            Messages(
                                messageId = System.currentTimeMillis() + 1,
                                userId = currentUserId,
                                chatRoomId = chatRoomId,
                                text = "안녕하세요, ${user.nickname}님!",
                                date = LocalDateTime.now().minusMinutes(30)
                            ),
                            Messages(
                                messageId = System.currentTimeMillis() + 2,
                                userId = user.userId,
                                chatRoomId = chatRoomId,
                                text = when(user.userId) {
                                    100L -> "와, 와이파이 없이 대화 신기하당 ㅎㅎ"
                                    101L -> "난 귀요미"
                                    102L -> "메시지 입력해봐.."
                                    103L -> "나만의 채로서 일타강사."
                                    else -> "여긴 어디? 난 누구?"
                                },
                                date = LocalDateTime.now().minusMinutes(15)
                            )
                        )
                        
                        // 메시지 저장
                        dummyMessages.forEach { message ->
                            messagesDao.insertMessage(message)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 오류 처리
            _uiState.update {
                it.copy(errorMessage = "더미 데이터 생성 중 오류: ${e.message}")
            }
        }
    }
    
    /**
     * 주변 사용자 생성 (BLE 스캔 결과를 모방한 더미 데이터)
     */
    private fun generateNearbyUsers() {
        viewModelScope.launch {
            val nearbyUsers = listOf(
                NearbyUser(id = 1, name = "도경원", distance = 30f),
                NearbyUser(id = 2, name = "도경원2", distance = 85f),
                NearbyUser(id = 3, name = "여자친구", distance = 150f),
                NearbyUser(id = 4, name = "친구1", distance = 220f),
                NearbyUser(id = 5, name = "친구2", distance = 310f),
                NearbyUser(id = 6, name = "친구3", distance = 50f),
                NearbyUser(id = 7, name = "친구4", distance = 180f),
                NearbyUser(id = 8, name = "친구5", distance = 270f)
            )
            
            _uiState.update {
                it.copy(nearbyUsers = nearbyUsers)
            }
        }
    }
    
    /**
     * 날짜/시간 형식 변환 함수
     */
    private fun formatDateTime(dateTime: LocalDateTime): String {
        val now = LocalDateTime.now()
        return when {
            dateTime.toLocalDate() == now.toLocalDate() -> {
                // 오늘이면 시간만 표시
                dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
            }
            dateTime.toLocalDate() == now.toLocalDate().minusDays(1) -> {
                // 어제면 "어제" 표시
                "어제"
            }
            dateTime.year == now.year -> {
                // 올해면 월/일 표시
                dateTime.format(DateTimeFormatter.ofPattern("MM/dd"))
            }
            else -> {
                // 작년 이전이면 년/월/일 표시
                dateTime.format(DateTimeFormatter.ofPattern("yy/MM/dd"))
            }
        }
    }
    
    /**
     * 채팅방 새로고침
     */
    fun refreshChatRooms() {
        loadChatRooms()
    }
    
    /**
     * 새 채팅방 생성
     */
    fun createChatRoom(participantId: Long) {
        viewModelScope.launch {
            try {
                val currentUserId = currentUser?.userId ?: return@launch
                
                // 이미 존재하는 채팅방인지 확인
                val existingRooms = chatRoomDao.getChatRoomsByParticipantId(currentUserId)
                val alreadyExists = existingRooms.any { it.participantId == participantId }
                
                if (!alreadyExists) {
                    // 새 채팅방 생성
                    val chatRoomId = chatRoomDao.InsertChatRoom(
                        ChatRoom(
                            chatRoomId = System.currentTimeMillis(),
                            updatedAt = LocalDateTime.now(),
                            participantId = participantId
                        )
                    )
                    
                    // 채팅방 목록 다시 로드
                    loadChatRooms()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "채팅방 생성 중 오류: ${e.message}")
                }
            }
        }
    }
} 