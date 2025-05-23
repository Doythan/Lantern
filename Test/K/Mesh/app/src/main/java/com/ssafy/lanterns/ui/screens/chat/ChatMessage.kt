package com.ssafy.lanterns.ui.screens.chat

import java.util.UUID

/**
 * 채팅 메시지 데이터 클래스
 */
data class ChatMessage(
    val id: UUID = UUID.randomUUID(),                 // 메시지 고유 ID
    val sender: Short,                                // 발신자 주소
    val senderNickname: String,                       // 발신자 닉네임
    val recipient: Short? = null,                     // 수신자 주소 (null인 경우 그룹 메시지)
    val content: String,                              // 메시지 내용
    val timestamp: Long = System.currentTimeMillis(), // 생성 시간
    val isIncoming: Boolean = true,                   // 수신 메시지 여부
    val isDelivered: Boolean = true,                  // 전송 완료 여부
    val isUrgent: Boolean = false,                    // 긴급 메시지 여부
    val messageStatus: MessageStatus = if (isDelivered) MessageStatus.DELIVERED else MessageStatus.SENDING  // 메시지 상태
) {
    // 그룹 메시지 여부
    val isGroupMessage: Boolean
        get() = recipient == null
        
    // 메시지 유형
    enum class MessageType {
        TEXT,       // 텍스트 메시지
        SYSTEM,     // 시스템 메시지
        ALERT       // 경고 메시지
    }
    
    // 메시지 상태
    enum class MessageStatus {
        SENDING,    // 전송 중
        DELIVERED,  // 전송 완료
        SEEN,       // 읽음
        FAILED      // 전송 실패
    }
    
    companion object {
        // 시스템 메시지 생성
        fun createSystemMessage(content: String): ChatMessage {
            return ChatMessage(
                sender = 0,
                senderNickname = "System",
                content = content,
                isIncoming = true,
                isDelivered = true
            )
        }
        
        // 전송 실패 메시지 생성 (원본 메시지 기반)
        fun createFailedMessage(original: ChatMessage): ChatMessage {
            return original.copy(
                isDelivered = false,
                messageStatus = MessageStatus.FAILED
            )
        }
    }
} 