package com.ssafy.lanterns.data.repository

import com.ssafy.lanterns.data.source.ble.mesh.ChatMessage
import com.ssafy.lanterns.data.source.ble.mesh.MeshMessage
import kotlinx.coroutines.flow.Flow

/**
 * 메시지 저장소 인터페이스
 * BLE 메쉬 네트워크 메시지 및 일반 채팅 메시지의 저장과 조회를 담당
 */
interface MessageRepository {
    /**
     * 메쉬 메시지 저장
     * @param message 저장할 메쉬 메시지
     * @return 저장 성공 여부
     */
    suspend fun saveMeshMessage(message: MeshMessage): Boolean
    
    /**
     * 채팅 메시지 저장
     * @param message 채팅 메시지
     * @return 저장 성공 여부
     */
    suspend fun saveChatMessage(message: ChatMessage): Boolean
    
    /**
     * 메시지 전송 상태 업데이트
     * @param messageId 업데이트할 메시지 ID
     * @param isDelivered 전송 완료 여부
     * @return 업데이트 성공 여부
     */
    suspend fun updateMessageStatus(messageId: String, isDelivered: Boolean): Boolean
    
    /**
     * 채팅 메시지 목록 가져오기
     * @param limit 가져올 메시지 수 제한
     * @return 메시지 목록
     */
    fun getChatMessages(limit: Int = 100): Flow<List<ChatMessage>>
    
    /**
     * 1:1 채팅 메시지 목록 가져오기
     * @param recipientAddress 상대방 주소
     * @param limit 가져올 메시지 수 제한
     * @return 메시지 목록
     */
    fun getDirectMessages(recipientAddress: Short, limit: Int = 100): Flow<List<ChatMessage>>
    
    /**
     * 그룹 채팅 메시지 목록 가져오기
     * @param limit 가져올 메시지 수 제한
     * @return 메시지 목록
     */
    fun getGroupMessages(limit: Int = 100): Flow<List<ChatMessage>>
    
    /**
     * 오래된 메시지 정리
     * @param daysToKeep 보관할 일수
     * @return 삭제된 메시지 수
     */
    suspend fun cleanupOldMessages(daysToKeep: Int = 7): Int
} 