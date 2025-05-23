package com.ssafy.lanterns.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 통화 기록 엔티티
 */
@Entity(tableName = "call_histories")
data class CallHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceAddress: String,
    val deviceName: String,
    val timestamp: Date,
    val duration: Int, // 통화 시간 (초)
    val isOutgoing: Boolean, // 발신 여부
    val isAnswered: Boolean // 응답 여부
) 