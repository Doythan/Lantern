package com.ssafy.lanterns.data.source.ble.mesh

import android.bluetooth.BluetoothDevice
import java.util.UUID
import com.ssafy.lanterns.data.source.ble.mesh.MeshMessage

/**
 * 채팅 메시지 데이터 클래스
 * UI 표시와 메쉬 네트워크 통신에 사용되는 메시지를 정의
 */
data class ChatMessage(
    val id: UUID = UUID.randomUUID(),
    val sender: Short,             // 발신자 주소
    val senderNickname: String,    // 발신자 닉네임
    val recipient: Short? = null,  // 수신자 주소 (null이면 그룹 메시지)
    val content: String,           // 메시지 내용
    val timestamp: Long = System.currentTimeMillis(),
    val isUrgent: Boolean = false, // 긴급 메시지 여부
    val isDelivered: Boolean = false, // 전송 완료 여부
    val isIncoming: Boolean = true // 수신 메시지 여부 (UI 표시용)
) {
    /**
     * MeshMessage로 변환
     * @param ttl Time To Live 값
     * @return MeshMessage 객체
     */
    fun toMeshMessage(ttl: Int = 5): MeshMessage {
        val messageType = when {
            isUrgent && recipient != null -> MessageType.URGENT_UNICAST
            isUrgent -> MessageType.URGENT_BROADCAST
            recipient != null -> MessageType.CHAT_UNICAST
            else -> MessageType.CHAT
        }
        
        return MeshMessage(
            sender = sender.toString(),
            senderNickname = senderNickname,
            sequenceNumber = id.leastSignificantBits.toInt(),
            messageType = messageType,
            content = content,
            timestamp = timestamp,
            ttl = ttl,
            target = recipient?.toString()
        )
    }
    
    companion object {
        /**
         * MeshMessage로부터 ChatMessage 생성
         * @param meshMessage MeshMessage 객체
         * @param isIncoming 수신 메시지 여부
         * @return ChatMessage 객체
         */
        fun fromMeshMessage(meshMessage: MeshMessage, isIncoming: Boolean = true): ChatMessage {
            val messageId = UUID(
                meshMessage.sequenceNumber.toLong() shl 32, 
                meshMessage.timestamp
            )
            
            return ChatMessage(
                id = messageId,
                sender = meshMessage.sender.toShortOrZero(),
                senderNickname = meshMessage.senderNickname,
                recipient = meshMessage.target?.toShortOrNull(),
                content = meshMessage.content,
                timestamp = meshMessage.timestamp,
                isUrgent = meshMessage.messageType == MessageType.URGENT_UNICAST || 
                           meshMessage.messageType == MessageType.URGENT_BROADCAST,
                isIncoming = isIncoming
            )
        }
    }
}

/**
 * 주변 노드 정보 데이터 클래스
 */
data class NearbyNode(
    val device: BluetoothDevice? = null,
    val address: Short? = null,    // 메시 네트워크 주소
    val nickname: String,         // 설정된 닉네임
    val rssi: Int = 0,            // 신호 강도
    val lastSeen: Long = System.currentTimeMillis() // 마지막 탐지 시간
)

/**
 * String을 Short로 변환 (실패 시 0 반환)
 */
private fun String.toShortOrZero(): Short {
    return try {
        this.toShort()
    } catch (e: NumberFormatException) {
        0
    }
}

/**
 * String을 Short로 변환 (실패 시 null 반환)
 */
private fun String.toShortOrNull(): Short? {
    return try {
        this.toShort()
    } catch (e: NumberFormatException) {
        null
    }
} 