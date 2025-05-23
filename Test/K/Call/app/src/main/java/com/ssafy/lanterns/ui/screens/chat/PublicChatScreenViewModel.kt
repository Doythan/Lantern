package com.ssafy.lanterns.ui.screens.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import com.ssafy.lanterns.data.model.User
import com.ssafy.lanterns.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.viewModelScope
import com.ssafy.lanterns.data.model.Messages
import com.ssafy.lanterns.data.repository.MessagesDao
import com.ssafy.lanterns.ui.components.ChatUser
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneOffset

// PublicChat을 위한 고정된 chatRoomId
private const val PUBLIC_CHAT_ROOM_ID = 0L
// 시스템 메시지 전용 User ID
private const val SYSTEM_USER_ID = -1L
// 알 수 없는 외부 발신자 User ID
private const val UNKNOWN_SENDER_USER_ID = -2L

/**
 * PublicChatScreen에서 UI 표시에 사용될 메시지 데이터 클래스
 */
data class ChatMessage(
    val id: Long, // DB의 messageId (고유 PK)
    val sender: String, // 닉네임
    val text: String,
    val time: Long, // 타임스탬프 (millis)
    val isMe: Boolean = false,
    val senderProfileId: Int? = null,
    val distance: Float = 0f, // PublicChat에서는 기본값 또는 미사용
    val isRelayed: Boolean = false // 릴레이된 메시지 여부
)

@HiltViewModel
class PublicChatScreenViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val messagesDao: MessagesDao
): ViewModel() {
    private val _currentUser = mutableStateOf<User?>(null)
    val currentUser: State<User?> = _currentUser

    private val _messages = mutableStateOf<List<ChatMessage>>(emptyList())
    val messages: State<List<ChatMessage>> = _messages
    
    // 채팅방 참여자 정보를 위한 State 추가
    private val _nearbyUsers = mutableStateOf<List<ChatUser>>(emptyList())
    val nearbyUsers: State<List<ChatUser>> = _nearbyUsers
    
    // 시스템 메시지 표시 여부를 제어하는 Flag 추가 (기본값 false로 시스템 메시지 제거)
    private val _showSystemMessages = mutableStateOf(false)
    val showSystemMessages: State<Boolean> = _showSystemMessages

    init {
        viewModelScope.launch {
            _currentUser.value = userRepository.getUser()
            loadMessages()
        }
    }

    private fun mapDbMessageToChatMessage(dbMessage: Messages, currentUserId: Long?): ChatMessage {
        val isMe = dbMessage.userId == currentUserId
        return ChatMessage(
            id = dbMessage.messageId, // DB의 messageId 사용
            sender = if (isMe) _currentUser.value?.nickname ?: "나"
            else if (dbMessage.userId == SYSTEM_USER_ID) "시스템"
            else _currentUser.value?.let { if (it.userId == dbMessage.userId) it.nickname else null } ?: "익명", // 임시로 익명 처리, 아래 발신자 닉네임 조회 로직 추가
            text = dbMessage.text,
            time = dbMessage.date.toInstant(ZoneOffset.UTC).toEpochMilli(), // LocalDateTime to Long (millis)
            isMe = isMe,
            senderProfileId = if (isMe) _currentUser.value?.selectedProfileImageNumber
            else if (dbMessage.userId == SYSTEM_USER_ID) -1 // 시스템 아이콘
            else _currentUser.value?.let { if (it.userId == dbMessage.userId) it.selectedProfileImageNumber else null } ?: 1, // 임시로 기본 프로필
            isRelayed = false // DB에서 불러온 메시지는 릴레이된 것이 아님 (또는 해당 정보가 없음)
        )
    }

    private suspend fun fetchSenderNickname(userId: Long): String {
        if (userId == SYSTEM_USER_ID) return "시스템"
        if (userId == UNKNOWN_SENDER_USER_ID) return "익명"
        return userRepository.getUserById(userId)?.nickname ?: "알 수 없음"
    }

    private fun loadMessages() {
        viewModelScope.launch {
            val dbMessages = messagesDao.getMessages(PUBLIC_CHAT_ROOM_ID, limit = 200, offset = 0) // 최근 200개
            
            // 시스템 메시지 필터링 (시스템 메시지 제거)
            val filteredMessages = dbMessages.filter { dbMsg ->
                dbMsg.userId != SYSTEM_USER_ID || _showSystemMessages.value
            }
            
            val mappedMessages = filteredMessages.map { dbMsg ->
                val senderNickname = fetchSenderNickname(dbMsg.userId)
                val senderProfileId = if (dbMsg.userId == SYSTEM_USER_ID) -1
                else userRepository.getUserById(dbMsg.userId)?.selectedProfileImageNumber ?: 1
                ChatMessage(
                    id = dbMsg.messageId,
                    sender = senderNickname,
                    text = dbMsg.text,
                    time = dbMsg.date.toInstant(ZoneOffset.UTC).toEpochMilli(),
                    isMe = dbMsg.userId == _currentUser.value?.userId,
                    senderProfileId = senderProfileId,
                    isRelayed = false // DB에서 불러온 메시지
                )
            }
            _messages.value = mappedMessages.reversed() // 최신 메시지가 아래로
            
            // 시스템 메시지 없이도 채팅방 초기화하도록 변경
            if (_messages.value.isEmpty() && _showSystemMessages.value) {
                initializeDefaultMessages()
            }
        }
    }

    // 시스템 메시지 표시 여부 설정 함수 추가
    fun setShowSystemMessages(show: Boolean) {
        if (_showSystemMessages.value != show) {
            _showSystemMessages.value = show
            loadMessages() // 메시지 다시 로드하여 필터링 적용
        }
    }

    fun initializeDefaultMessages() {
        viewModelScope.launch {
            val systemMessageText = "모두의 광장에 오신 것을 환영합니다. 주변 사람들과 자유롭게 대화해보세요!"
            val existingSystemMessage = messagesDao.getMessages(PUBLIC_CHAT_ROOM_ID, 1, 0)
                .firstOrNull { it.text == systemMessageText && it.userId == SYSTEM_USER_ID }

            if (existingSystemMessage == null) {
                val systemMessage = Messages(
                    messageId = System.currentTimeMillis(), // 고유 ID 생성
                    userId = SYSTEM_USER_ID,
                    chatRoomId = PUBLIC_CHAT_ROOM_ID,
                    text = systemMessageText,
                    date = LocalDateTime.now().minusHours(1)
                )
                messagesDao.insertMessage(systemMessage)
                loadMessages()
            }
        }
    }
    
    // 채팅 참여자 추가 함수
    fun addNearbyUser(chatUser: ChatUser) {
        val currentUsers = _nearbyUsers.value.toMutableList()
        // 중복 체크: 이름이 같은 사용자가 있으면 업데이트만 하고 넘어감
        val existingUserIndex = currentUsers.indexOfFirst { it.name == chatUser.name }
        
        if (existingUserIndex != -1) {
            // 기존 사용자 정보 업데이트 (거리, 메시지 카운트 등)
            currentUsers[existingUserIndex] = currentUsers[existingUserIndex].copy(
                distance = chatUser.distance,
                messageCount = currentUsers[existingUserIndex].messageCount + 1
            )
        } else {
            // 새 사용자 추가
            currentUsers.add(chatUser)
        }
        
        _nearbyUsers.value = currentUsers
    }

    fun addMessage(chatMessageUi: ChatMessage) {
        viewModelScope.launch {
            val currentUserId = _currentUser.value?.userId
            var rawSenderFromBle = chatMessageUi.sender // Scanner에서 온 값 (형식: "닉네임" 또는 "닉네임|메시지부분2")
            var rawTextFromBle = chatMessageUi.text   // Scanner에서 온 값 (형식: "메시지부분1")
            val isRelayedMessage = chatMessageUi.isRelayed // Scanner에서 전달된 릴레이 여부

            var finalSenderNickname = "익명" // 최종적으로 사용할 닉네임
            var finalMessageText = ""    // 최종적으로 사용할 메시지 본문

            Log.d("PublicChatVM_AddMsg", "수신된 ChatMessageUI: sender='${rawSenderFromBle}', text='${rawTextFromBle}', isRelayed=$isRelayedMessage")

            // --- ViewModel 레벨에서 닉네임 및 메시지 재구성 시도 ---
            if (chatMessageUi.isMe) {
                finalSenderNickname = _currentUser.value?.nickname ?: "나"
                finalMessageText = rawTextFromBle // 내 메시지는 Scanner를 거치지 않으므로 text가 전체 메시지
            } else {
                val senderParts = rawSenderFromBle.split("|", limit = 2)
                val potentialNickname = senderParts.getOrNull(0)?.takeIf { it.isNotBlank() }

                if (potentialNickname != null) {
                    finalSenderNickname = potentialNickname
                    finalMessageText = rawTextFromBle + (senderParts.getOrNull(1) ?: "")
                } else {
                    finalSenderNickname = rawSenderFromBle.takeIf { it.isNotBlank() } ?: "익명"
                    finalMessageText = rawTextFromBle
                }
            }
            Log.d("PublicChatVM_AddMsg", "ViewModel 파싱 후: Nickname='${finalSenderNickname}', Message='${finalMessageText}' (원본 sender='${rawSenderFromBle}', text='${rawTextFromBle}')")
            // --- ViewModel 레벨 파싱 끝 ---


            var senderUser = if (chatMessageUi.isMe) {
                _currentUser.value
            } else {
                userRepository.getUserByNickname(finalSenderNickname)
            }

            if (senderUser == null && !chatMessageUi.isMe && finalSenderNickname != "익명") {
                // 새로운 User 객체 생성 (userId는 임시로 현재 시간 또는 다른 고유값 사용, deviceId도 필요시 설정)
                // 중요: User 엔티티의 userId는 autoGenerate = false 이므로 직접 할당 필요
                val newUserId = System.currentTimeMillis() // 예시: 임시 ID, 실제로는 더 견고한 ID 생성 방식 필요
                val newUser = User(
                    userId = newUserId, // 고유 ID 할당 필요
                    nickname = finalSenderNickname,
                    deviceId = "UnknownDevice-${newUserId}", // 임시 deviceId
                    selectedProfileImageNumber = 1, // 기본 프로필 이미지
                    email = "$finalSenderNickname@example.com" // 임시 이메일
                )
                // userRepository.saveUser(newUser)를 직접 호출하면 deleteAllUsers() 때문에 문제 발생
                // userDao.insertUser(newUser) 또는 UserRepository에 새 사용자 추가용 함수 필요
                // 예: userRepository.insertLocalUser(newUser)
                try {
                    userRepository.insertLocalUser(newUser) // UserRepositoryImpl에 이 함수 구현 필요 (아래 참고)
                    senderUser = newUser // 새로 저장된 사용자 정보로 업데이트
                    Log.d("PublicChatVM_AddMsg", "새로운 사용자 '${finalSenderNickname}' (ID: ${newUser.userId}) 정보를 DB에 저장했습니다.")
                } catch (e: Exception) {
                    Log.e("PublicChatVM_AddMsg", "새로운 사용자 '${finalSenderNickname}' 저장 실패: ${e.message}")
                    // 저장 실패 시 senderUser는 여전히 null일 수 있음, 이 경우 UNKNOWN_SENDER_USER_ID 사용
                }
            }

            val senderUserIdToSaveInMessage = senderUser?.userId ?: UNKNOWN_SENDER_USER_ID


            val dbMessage = Messages(
                messageId = 0L, // Room에서 자동 생성되도록 (엔티티에서 @PrimaryKey(autoGenerate = true) 필요)
                userId = senderUserIdToSaveInMessage,
                chatRoomId = PUBLIC_CHAT_ROOM_ID,
                text = finalMessageText, // ViewModel에서 재구성한 메시지 사용
                date = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(chatMessageUi.time),
                    ZoneOffset.UTC
                )
            )

            val insertedMessageId = messagesDao.insertMessage(dbMessage)
            val savedDbMessage = messagesDao.getMessageById(insertedMessageId) // DAO에 getMessageById 추가 필요 (이전 답변 참고)

            if (savedDbMessage == null) {
                Log.e("PublicChatVM", "메시지 저장 후 DB에서 찾을 수 없음: ID=$insertedMessageId. 임시 UI 업데이트 사용.")
                val tempChatMessage = chatMessageUi.copy(
                    id = insertedMessageId,
                    sender = finalSenderNickname,
                    text = finalMessageText,
                    isMe = (senderUserIdToSaveInMessage == currentUserId),
                    senderProfileId = senderUser?.selectedProfileImageNumber ?: 1,
                    isRelayed = isRelayedMessage // 릴레이 여부 유지
                )
                _messages.value = listOf(tempChatMessage) + _messages.value.filterNot { it.id == 0L && it.time == tempChatMessage.time }
                return@launch
            }

            // DB에서 성공적으로 가져온 메시지 기반으로 ChatMessage UI 객체 생성
            val newChatMessage = ChatMessage(
                id = savedDbMessage.messageId,
                sender = senderUser?.nickname ?: finalSenderNickname.takeIf { it.isNotBlank() } ?: "익명",
                text = savedDbMessage.text,
                time = savedDbMessage.date.toInstant(ZoneOffset.UTC).toEpochMilli(),
                isMe = (savedDbMessage.userId == currentUserId),
                senderProfileId = senderUser?.selectedProfileImageNumber ?: 1,
                isRelayed = isRelayedMessage // 릴레이 여부 전달
            )
            _messages.value = listOf(newChatMessage) + _messages.value.filterNot { it.id == 0L && it.time == newChatMessage.time }
        }
    }

    // getNextMessageId 함수는 DB의 messageId가 자동 증가 PK일 경우 필요 없을 수 있음.
    // 현재는 ChatMessage의 임시 ID 생성용으로 유지.
    fun getNextMessageId(): Long { // 반환 타입을 Long으로 변경
        return (_messages.value.maxOfOrNull { it.id } ?: 0L) + 1L
    }
}