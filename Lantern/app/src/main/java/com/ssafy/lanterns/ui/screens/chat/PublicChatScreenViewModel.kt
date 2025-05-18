package com.ssafy.lanterns.ui.screens.chat

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
    val distance: Float = 0f // PublicChat에서는 기본값 또는 미사용
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
            else _currentUser.value?.let { if (it.userId == dbMessage.userId) it.selectedProfileImageNumber else null } ?: 1 // 임시로 기본 프로필
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
            val mappedMessages = dbMessages.map { dbMsg ->
                val senderNickname = fetchSenderNickname(dbMsg.userId)
                val senderProfileId = if (dbMsg.userId == SYSTEM_USER_ID) -1
                else userRepository.getUserById(dbMsg.userId)?.selectedProfileImageNumber ?: 1
                ChatMessage(
                    id = dbMsg.messageId,
                    sender = senderNickname,
                    text = dbMsg.text,
                    time = dbMsg.date.toInstant(ZoneOffset.UTC).toEpochMilli(),
                    isMe = dbMsg.userId == _currentUser.value?.userId,
                    senderProfileId = senderProfileId
                )
            }
            _messages.value = mappedMessages.reversed() // 최신 메시지가 아래로

            if (_messages.value.isEmpty()) {
                initializeDefaultMessages()
            }
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

    fun addMessage(chatMessageUi: ChatMessage) { // 파라미터 이름을 chatMessageUi로 변경하여 명확화
        viewModelScope.launch {
            val currentUserId = _currentUser.value?.userId
            val senderUserId = if (chatMessageUi.isMe) {
                currentUserId ?: return@launch // 내가 보낸 메시지인데 사용자 ID가 없으면 전송 불가
            } else {
                // 외부에서 수신된 메시지의 경우, sender 닉네임으로 userId를 찾아야 함.
                // BLE 스캔 시 sender의 고유 ID(userId)를 함께 받아오는 것이 가장 이상적입니다.
                // 현재는 닉네임 기반으로 조회하며, 없을 경우 UNKNOWN_SENDER_USER_ID 사용
                userRepository.getUserByNickname(chatMessageUi.sender)?.userId ?: UNKNOWN_SENDER_USER_ID
            }

            val dbMessage = Messages(
                messageId = System.currentTimeMillis(), // 실제로는 DB에서 자동 생성되도록 하는 것이 좋음
                userId = senderUserId,
                chatRoomId = PUBLIC_CHAT_ROOM_ID,
                text = chatMessageUi.text,
                date = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(chatMessageUi.time), ZoneOffset.UTC)
            )
            val insertedMessageId = messagesDao.insertMessage(dbMessage) // insertMessage가 Long ID를 반환하도록 수정 필요 (현재는 Long)

            // DB에 삽입된 메시지를 기준으로 ChatMessage 객체 다시 생성 (ID 동기화)
            val newChatMessage = ChatMessage(
                id = insertedMessageId, // DB에서 반환된 ID 사용 (MessagesDao 수정 필요)
                sender = chatMessageUi.sender,
                text = chatMessageUi.text,
                time = chatMessageUi.time,
                isMe = chatMessageUi.isMe,
                senderProfileId = if (chatMessageUi.isMe) _currentUser.value?.selectedProfileImageNumber
                else userRepository.getUserById(senderUserId)?.selectedProfileImageNumber ?: 1
            )
            _messages.value = listOf(newChatMessage) + _messages.value
        }
    }

    // getNextMessageId 함수는 DB의 messageId가 자동 증가 PK일 경우 필요 없을 수 있음.
    // 현재는 ChatMessage의 임시 ID 생성용으로 유지.
    fun getNextMessageId(): Long { // 반환 타입을 Long으로 변경
        return (_messages.value.maxOfOrNull { it.id } ?: 0L) + 1L
    }
}