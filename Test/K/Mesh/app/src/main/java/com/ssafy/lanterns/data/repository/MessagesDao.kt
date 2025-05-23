package com.ssafy.lanterns.data.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ssafy.lanterns.data.model.Messages
import java.time.LocalDateTime

@Dao
interface MessagesDao {

    // 메세지 저장
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Messages): Long
    
    // 별칭 메서드 - 기존 코드 호환성 유지
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: Messages): Long
    
    // 메시지 업데이트
    @Update
    suspend fun update(message: Messages)
    
    // 메시지 ID로 조회
    @Query("SELECT * FROM messages WHERE message_id = :messageId")
    suspend fun getMessageById(messageId: Long): Messages?

    /**
     * 특정 채팅방의 모든 메시지를 삭제합니다.
     * @param chatRoomId 삭제할 메시지들의 chat_room_id
     * @return 삭제된 행(row) 수
     */
    @Query("""
        DELETE FROM messages
        WHERE chat_room_id = :chatRoomId
    """)
    suspend fun deleteMessagesByChatRoomId(chatRoomId: Long): Int


    // 메세지 불러오기
    @Query("""
        SELECT * FROM messages
        WHERE chat_room_id = :chatRoomId
        ORDER BY date DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getMessages(
        chatRoomId: Long,
        limit: Int,
        offset: Int
    ): List<Messages>


    // 스크롤 업 방식 페이징
    @Query("""
        SELECT * FROM messages
        WHERE chat_room_id = :chatRoomId
          AND date < :beforeDate
        ORDER BY date DESC
        LIMIT :limit
    """)
    suspend fun getMessagesBefore(
        chatRoomId: Long,
        beforeDate: LocalDateTime,
        limit: Int
    ): List<Messages>



}