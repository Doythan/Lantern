package com.ssafy.lanterns.model

import kotlin.random.Random

/**
 * 채팅 메시지 데이터 클래스
 */
data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val sender: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val senderProfileId: Int = Random.nextInt(1, 6),
    val distance: Float = 0f // 기본값은 0 (자신)
) 