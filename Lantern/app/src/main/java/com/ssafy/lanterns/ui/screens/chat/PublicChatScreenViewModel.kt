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

    fun addMessage(chatMessageUi: ChatMessage) {
        viewModelScope.launch {
            val currentUserId = _currentUser.value?.userId
            var rawSenderFromBle = chatMessageUi.sender // Scanner에서 온 값 (형식: "닉네임" 또는 "닉네임|메시지부분2")
            var rawTextFromBle = chatMessageUi.text   // Scanner에서 온 값 (형식: "메시지부분1")

            var finalSenderNickname = "익명" // 최종적으로 사용할 닉네임
            var finalMessageText = ""    // 최종적으로 사용할 메시지 본문

            // --- ViewModel 레벨에서 닉네임 및 메시지 재구성 시도 ---
            if (chatMessageUi.isMe) {
                finalSenderNickname = _currentUser.value?.nickname ?: "나"
                finalMessageText = rawTextFromBle // 내 메시지는 Scanner를 거치지 않으므로 text가 전체 메시지
            } else {
                // rawSenderFromBle 의 형식 가정: "닉네임" 또는 "닉네임|메시지부분2"
                // rawTextFromBle 의 형식 가정: "메시지부분1" (uuid는 Scanner에서 이미 처리됨)

                val senderParts = rawSenderFromBle.split("|", limit = 2)
                val potentialNickname = senderParts.getOrNull(0)?.takeIf { it.isNotBlank() }

                if (potentialNickname != null) {
                    finalSenderNickname = potentialNickname
                    // 메시지 조합: Scanner에서 온 text (메시지부분1) + senderParts에서 나온 메시지부분2 (있을 경우)
                    finalMessageText = rawTextFromBle + (senderParts.getOrNull(1) ?: "")
                } else {
                    // rawSenderFromBle에서 닉네임을 추출할 수 없는 경우
                    // 이 경우는 ScannerManager에서 이미 "익명"으로 처리되었을 가능성이 높음
                    finalSenderNickname = rawSenderFromBle.takeIf { it.isNotBlank() } ?: "익명"
                    finalMessageText = rawTextFromBle
                }

                // 만약 rawTextFromBle 에도 '|' 구분자가 있고, 닉네임 정보가 포함된 다른 형식일 가능성도 고려?
                // (현재 BLE 프로토콜 상으로는 그럴 가능성 낮음)
                // 예: if (rawTextFromBle.contains("|") && finalSenderNickname == "익명") { ... }
            }
            Log.d("PublicChatVM_AddMsg", "ViewModel 파싱 후: Nickname='${finalSenderNickname}', Message='${finalMessageText}' (원본 sender='${rawSenderFromBle}', text='${rawTextFromBle}')")
            // --- ViewModel 레벨 파싱 끝 ---


            val senderUserId = if (chatMessageUi.isMe) {
                currentUserId ?: run {
                    Log.e("PublicChatVM", "현재 사용자 ID를 알 수 없어 메시지를 저장할 수 없습니다.")
                    return@launch
                }
            } else {
                userRepository.getUserByNickname(finalSenderNickname)?.userId ?: UNKNOWN_SENDER_USER_ID
            }

            val dbMessage = Messages(
                messageId = 0L, // Room에서 자동 생성되도록 (엔티티에서 @PrimaryKey(autoGenerate = true) 필요)
                userId = senderUserId,
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
                // DB 저장 실패 시, BLE로 받은 값 기반으로 임시 UI 업데이트 (닉네임 정확도 낮을 수 있음)
                val tempChatMessage = chatMessageUi.copy(
                    id = insertedMessageId, // 임시 ID라도 할당
                    sender = finalSenderNickname,
                    text = finalMessageText,
                    isMe = (senderUserId == currentUserId),
                    senderProfileId = if (senderUserId == currentUserId) _currentUser.value?.selectedProfileImageNumber
                    else userRepository.getUserById(senderUserId)?.selectedProfileImageNumber ?: 1
                )
                _messages.value = listOf(tempChatMessage) + _messages.value.filterNot { it.id == 0L && it.time == tempChatMessage.time }
                return@launch
            }

            // DB에서 성공적으로 가져온 메시지 기반으로 ChatMessage UI 객체 생성
            val newChatMessage = ChatMessage(
                id = savedDbMessage.messageId,
                sender = if (savedDbMessage.userId == currentUserId) {
                    _currentUser.value?.nickname ?: "나"
                } else {
                    // DB에 저장된 userId로 닉네임 다시 조회 (정확도 향상)
                    // 또는 finalSenderNickname 사용 (BLE 수신 값을 더 신뢰할 경우)
                    userRepository.getUserById(savedDbMessage.userId)?.nickname
                        ?: finalSenderNickname.takeIf { it.isNotBlank() } // DB에 없으면 BLE 값
                        ?: "익명" // 둘 다 없으면 익명
                },
                text = savedDbMessage.text,
                time = savedDbMessage.date.toInstant(ZoneOffset.UTC).toEpochMilli(),
                isMe = (savedDbMessage.userId == currentUserId),
                senderProfileId = if (savedDbMessage.userId == currentUserId) {
                    _currentUser.value?.selectedProfileImageNumber
                } else {
                    userRepository.getUserById(savedDbMessage.userId)?.selectedProfileImageNumber ?: 1
                }
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