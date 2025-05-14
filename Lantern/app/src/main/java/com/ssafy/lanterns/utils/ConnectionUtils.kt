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