package com.example.blemodule.ble

import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * BLE 채팅에 사용되는 메시지 클래스
 */
data class BleMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderName: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val messageType: MessageType = MessageType.CHAT
) {
    /**
     * 메시지를 바이트 배열로 변환
     */
    fun toByteArray(): ByteArray {
        // 간단한 형식: 메시지타입|발신자|내용|타임스탬프|ID
        val messageString = "${messageType.name}|$senderName|$content|$timestamp|$id"
        return messageString.toByteArray(StandardCharsets.UTF_8)
    }

    companion object {
        /**
         * 바이트 배열에서 메시지 객체 생성
         */
        fun fromByteArray(bytes: ByteArray): BleMessage? {
            return try {
                val messageString = String(bytes, StandardCharsets.UTF_8)
                val parts = messageString.split("|")
                
                if (parts.size >= 5) {
                    BleMessage(
                        id = parts[4],
                        senderName = parts[1],
                        content = parts[2],
                        timestamp = parts[3].toLongOrNull() ?: System.currentTimeMillis(),
                        messageType = try {
                            MessageType.valueOf(parts[0])
                        } catch (e: IllegalArgumentException) {
                            MessageType.CHAT
                        }
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * 메시지 타입 열거형
 */
enum class MessageType {
    CHAT,       // 일반 채팅 메시지
    SYSTEM,     // 시스템 메시지 (연결 알림 등)
    BROADCAST,  // 브로드캐스트 메시지
    ENCRYPTED   // 암호화된 메시지
}
