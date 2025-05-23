package com.ssafy.lanterns.data.repository

import com.ssafy.lanterns.data.model.CallHistory
import kotlinx.coroutines.flow.Flow

/**
 * 통화 기록 리포지토리 인터페이스
 */
interface CallHistoryRepository {
    /**
     * 모든 통화 기록 조회 (최신순)
     */
    fun getAllCallHistories(): Flow<List<CallHistory>>
    
    /**
     * 특정 디바이스 주소의 통화 기록 조회
     */
    fun getCallHistoriesByDevice(deviceAddress: String): Flow<List<CallHistory>>
    
    /**
     * 통화 기록 추가
     */
    suspend fun insertCallHistory(callHistory: CallHistory)
    
    /**
     * 통화 기록 삭제
     */
    suspend fun deleteCallHistory(id: Long)
    
    /**
     * 모든 통화 기록 삭제
     */
    suspend fun clearAllCallHistories()
} 