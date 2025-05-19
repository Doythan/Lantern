package com.ssafy.lanterns.utils

import java.util.LinkedList
import kotlin.math.abs

object SignalStrengthManager {
    private const val MAX_RSSI_SAMPLES = 5 // 이동 평균 필터를 위한 샘플 수
    private val rssiHistory = mutableMapOf<String, LinkedList<Int>>() // Key: BLE Address

    // RSSI 임계값 (실제 환경에서 테스트 후 정밀 조정 필요)
    private const val RSSI_STRONG_THRESHOLD = -70 // 이 값보다 크거나 같으면 "강함" (Level 3)
    private const val RSSI_MEDIUM_THRESHOLD = -85 // 이 값보다 크거나 같으면 "중간" (Level 2)
    // 그 외는 "약함" (Level 1)

    fun getSmoothedRssi(bleAddress: String, newRssi: Int): Int {
        val history = rssiHistory.getOrPut(bleAddress) { LinkedList() }
        history.add(newRssi)
        if (history.size > MAX_RSSI_SAMPLES) {
            history.removeFirst()
        }
        return if (history.isEmpty()) newRssi else history.average().toInt()
    }

    fun calculateSignalLevelFromRssi(smoothedRssi: Int): Int {
        return when {
            smoothedRssi >= RSSI_STRONG_THRESHOLD -> 3 // 강함
            smoothedRssi >= RSSI_MEDIUM_THRESHOLD -> 2 // 중간
            else -> 1 // 약함
        }
    }

    fun clearHistoryForDevice(bleAddress: String) {
        rssiHistory.remove(bleAddress)
    }

    fun clearAllHistory() {
        rssiHistory.clear()
    }
} 