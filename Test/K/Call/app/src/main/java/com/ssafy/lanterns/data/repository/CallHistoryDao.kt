package com.ssafy.lanterns.data.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ssafy.lanterns.data.model.CallHistory
import kotlinx.coroutines.flow.Flow

/**
 * 통화 기록 DAO 인터페이스
 */
@Dao
interface CallHistoryDao {
    /**
     * 모든 통화 기록 조회 (최신순)
     */
    @Query("SELECT * FROM call_histories ORDER BY timestamp DESC")
    fun getAllCallHistories(): Flow<List<CallHistory>>
    
    /**
     * 특정 디바이스 주소의 통화 기록 조회
     */
    @Query("SELECT * FROM call_histories WHERE deviceAddress = :deviceAddress ORDER BY timestamp DESC")
    fun getCallHistoriesByDevice(deviceAddress: String): Flow<List<CallHistory>>
    
    /**
     * 통화 기록 추가
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallHistory(callHistory: CallHistory)
    
    /**
     * 통화 기록 삭제
     */
    @Query("DELETE FROM call_histories WHERE id = :id")
    suspend fun deleteCallHistory(id: Long)
    
    /**
     * 모든 통화 기록 삭제
     */
    @Query("DELETE FROM call_histories")
    suspend fun clearAllCallHistories()
} 