package com.ssafy.lanterns.data.repository

import com.ssafy.lanterns.data.model.CallHistory
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 통화 기록 리포지토리 구현 클래스
 */
@Singleton
class CallHistoryRepositoryImpl @Inject constructor(
    private val callHistoryDao: CallHistoryDao
) : CallHistoryRepository {
    
    override fun getAllCallHistories(): Flow<List<CallHistory>> {
        return callHistoryDao.getAllCallHistories()
    }
    
    override fun getCallHistoriesByDevice(deviceAddress: String): Flow<List<CallHistory>> {
        return callHistoryDao.getCallHistoriesByDevice(deviceAddress)
    }
    
    override suspend fun insertCallHistory(callHistory: CallHistory) {
        callHistoryDao.insertCallHistory(callHistory)
    }
    
    override suspend fun deleteCallHistory(id: Long) {
        callHistoryDao.deleteCallHistory(id)
    }
    
    override suspend fun clearAllCallHistories() {
        callHistoryDao.clearAllCallHistories()
    }
} 