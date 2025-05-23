package com.ssafy.lanterns.data.repository

import android.util.Log
import com.ssafy.lanterns.data.model.Messages
import com.ssafy.lanterns.data.source.ble.mesh.ChatMessage
import com.ssafy.lanterns.data.source.ble.mesh.MeshMessage
import com.ssafy.lanterns.data.source.ble.mesh.MessageDatabaseManager
import com.ssafy.lanterns.data.source.ble.mesh.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 메시지 저장소 구현체
 * MessageDatabaseManager와 Room 데이터베이스를 통합하여 메시지 저장소 역할 수행
 */
@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val messageDatabase: MessageDatabaseManager,
    private val messagesDao: MessagesDao
) : MessageRepository {
    private val TAG = "MessageRepositoryImpl"
    
    /**
     * 메쉬 메시지 저장
     * @param message 저장할 메쉬 메시지
     * @return 저장 성공 여부
     */
    override suspend fun saveMeshMessage(message: MeshMessage): Boolean = withContext(Dispatchers.IO) {
        try {
            // 중복 메시지 검사
            if (messageDatabase.hasMessage(message.sender, message.sequenceNumber.toLong())) {
                Log.d(TAG, "메시지가 이미 존재합니다: ${message.sequenceNumber}")
                return@withContext false
            }
            
            return@withContext messageDatabase.saveMessage(message)
        } catch (e: Exception) {
            Log.e(TAG, "메쉬 메시지 저장 실패", e)
            return@withContext false
        }
    }
    
    /**
     * 채팅 메시지 저장
     * @param message 채팅 메시지
     * @return 저장 성공 여부
     */
    override suspend fun saveChatMessage(message: ChatMessage): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. 로컬 SQLite DB에 MeshMessage로 저장
            val meshMessage = message.toMeshMessage()
            val savedToMeshDb = saveMeshMessage(meshMessage)
            
            // 2. Room DB에 Messages로 저장
            val messageEntity = Messages(
                messageId = message.id.mostSignificantBits,
                userId = message.sender.toLong(),
                chatRoomId = message.recipient?.toLong() ?: 0L, // 수신자가 null인 경우 그룹 채팅으로 간주
                text = message.content,
                date = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(message.timestamp),
                    ZoneId.systemDefault()
                )
            )
            messagesDao.insert(messageEntity)
            
            return@withContext savedToMeshDb
        } catch (e: Exception) {
            Log.e(TAG, "채팅 메시지 저장 실패", e)
            return@withContext false
        }
    }
    
    /**
     * 메시지 전송 상태 업데이트
     * @param messageId 업데이트할 메시지 ID
     * @param isDelivered 전송 완료 여부
     * @return 업데이트 성공 여부
     */
    override suspend fun updateMessageStatus(messageId: String, isDelivered: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. SQLite DB의 메시지 상태 업데이트 (현재 MessageDatabaseManager에 해당 기능이 없으므로 NOOP)
            // TODO: MessageDatabaseManager에 업데이트 메서드 추가 필요
            
            // 2. Room DB의 메시지 상태 업데이트
            val messageUUID = UUID.fromString(messageId)
            val messageEntity = messagesDao.getMessageById(messageUUID.mostSignificantBits)
            if (messageEntity != null) {
                messageEntity.isDelivered = isDelivered
                messagesDao.update(messageEntity)
                return@withContext true
            }
            
            Log.w(TAG, "메시지를 찾을 수 없음: $messageId")
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "메시지 상태 업데이트 실패", e)
            return@withContext false
        }
    }
    
    /**
     * 채팅 메시지 목록 가져오기
     * @param limit 가져올 메시지 수 제한
     * @return 메시지 목록
     */
    override fun getChatMessages(limit: Int): Flow<List<ChatMessage>> = flow {
        try {
            // SQLite DB에서 메시지 로드
            val meshMessages = messageDatabase.loadMessages(limit)
            
            // MeshMessage를 ChatMessage로 변환
            val chatMessages = meshMessages.map { meshMessage ->
                ChatMessage.fromMeshMessage(meshMessage)
            }
            
            emit(chatMessages)
        } catch (e: Exception) {
            Log.e(TAG, "채팅 메시지 목록 가져오기 실패", e)
            emit(emptyList<ChatMessage>())
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 1:1 채팅 메시지 목록 가져오기
     * @param recipientAddress 상대방 주소
     * @param limit 가져올 메시지 수 제한
     * @return 메시지 목록
     */
    override fun getDirectMessages(recipientAddress: Short, limit: Int): Flow<List<ChatMessage>> = flow {
        try {
            // SQLite DB에서 1:1 메시지 로드
            val meshMessages = messageDatabase.loadDirectMessages(recipientAddress.toString(), limit)
            
            // MeshMessage를 ChatMessage로 변환
            val chatMessages = meshMessages.map { meshMessage ->
                ChatMessage.fromMeshMessage(meshMessage)
            }
            
            emit(chatMessages)
        } catch (e: Exception) {
            Log.e(TAG, "1:1 채팅 메시지 목록 가져오기 실패", e)
            emit(emptyList<ChatMessage>())
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 그룹 채팅 메시지 목록 가져오기
     * @param limit 가져올 메시지 수 제한
     * @return 메시지 목록
     */
    override fun getGroupMessages(limit: Int): Flow<List<ChatMessage>> = flow {
        try {
            // SQLite DB에서 메시지 로드 (target이 null인 메시지만)
            val meshMessages = messageDatabase.loadMessages(limit).filter { it.target == null }
            
            // MeshMessage를 ChatMessage로 변환
            val chatMessages = meshMessages.map { meshMessage ->
                ChatMessage.fromMeshMessage(meshMessage)
            }
            
            emit(chatMessages)
        } catch (e: Exception) {
            Log.e(TAG, "그룹 채팅 메시지 목록 가져오기 실패", e)
            emit(emptyList<ChatMessage>())
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 오래된 메시지 정리
     * @param daysToKeep 보관할 일수
     * @return 삭제된 메시지 수
     */
    override suspend fun cleanupOldMessages(daysToKeep: Int): Int = withContext(Dispatchers.IO) {
        try {
            // SQLite DB에서 오래된 메시지 정리
            val deletedCount = messageDatabase.cleanupOldMessages(daysToKeep)
            Log.d(TAG, "삭제된 메시지 수: $deletedCount")
            
            // TODO: Room DB의 오래된 메시지도 정리 필요
            
            return@withContext deletedCount
        } catch (e: Exception) {
            Log.e(TAG, "오래된 메시지 정리 실패", e)
            return@withContext 0
        }
    }
} 