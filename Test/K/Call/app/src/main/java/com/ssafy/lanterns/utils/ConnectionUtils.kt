package com.ssafy.lanterns.utils

import androidx.compose.ui.graphics.Color
import com.ssafy.lanterns.ui.theme.ConnectionFar
import com.ssafy.lanterns.ui.theme.ConnectionMedium
import com.ssafy.lanterns.ui.theme.ConnectionNear

/**
 * 거리에 따라 적절한 연결 강도 색상을 반환합니다.
 * - 0-99m: 초록색 (ConnectionNear)
 * - 100-299m: 노란색 (ConnectionMedium)
 * - 300m 이상: 빨간색 (ConnectionFar)
 * 
 * @param distance 거리 (미터 단위)
 * @return 거리에 따른 색상
 */
fun getConnectionColorByDistance(distance: Float): Color {
    return when {
        distance < 100f -> ConnectionNear
        distance < 300f -> ConnectionMedium
        else -> ConnectionFar
    }
}

/**
 * 신호 강도 수준에 따라 적절한 연결 강도 색상을 반환합니다.
 * - 3(강함): 초록색 (ConnectionNear)
 * - 2(중간): 노란색 (ConnectionMedium)
 * - 1(약함): 빨간색 (ConnectionFar)
 * 
 * @param signalLevel 신호 강도 수준 (1-3)
 * @return 신호 강도에 따른 색상
 */
fun getConnectionColorBySignalLevel(signalLevel: Int): Color {
    return when (signalLevel) {
        3 -> ConnectionNear    // 강함
        2 -> ConnectionMedium  // 중간
        1 -> ConnectionFar     // 약함
        else -> Color.Gray     // 알 수 없음 또는 기본값
    }
}

/**
 * 두 값 사이를 선형 보간합니다.
 * (현재 사용되지 않지만 향후 필요할 수 있어 유지)
 */
private fun lerp(start: Int, end: Int, ratio: Float = 0.5f): Int {
    return (start + (end - start) * ratio).toInt()
}

/**
 * 거리에 따른 연결 강도 텍스트를 반환합니다.
 * 
 * @param distance 거리 (미터 단위)
 * @return 연결 강도 설명 문자열
 */
fun getConnectionStrengthText(distance: Float): String {
    return when {
        distance < 100f -> "강한 연결"
        distance < 300f -> "중간 연결"
        else -> "약한 연결"
    }
}

/**
 * 신호 강도 수준에 따른 연결 강도 텍스트를 반환합니다.
 * 
 * @param signalLevel 신호 강도 수준 (1-3)
 * @return 연결 강도 설명 문자열
 */
fun getConnectionStrengthTextFromSignalLevel(signalLevel: Int): String {
    return when (signalLevel) {
        3 -> "강함"
        2 -> "중간"
        1 -> "약함"
        else -> "알수없음"
    }
} 