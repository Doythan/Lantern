package com.ssafy.lanterns.ui.screens.main.components

import kotlin.random.Random

/**
 * 주변 사람 데이터 모델 (Depth 기반 구현)
 */
data class NearbyPerson(
    val serverUserIdString: String, // 서버에서 발급된 userId의 문자열 표현
    val nickname: String,
    val calculatedVisualDepth: Int, // UI 표시용: 상대방이 광고한 자신의 Depth + 1
    val advertisedDeviceDepth: Int, // 정보용: 상대방이 스스로 광고한 자신의 Depth
    val rssi: Int,                  // 스캔 시 수신된 (필터링된) RSSI
    val signalLevel: Int,           // 계산된 신호 강도 레벨 (1:약함, 2:중간, 3:강함)
    val angle: Float,               // UI 표시용 랜덤 각도 (serverUserIdString 기반)
    val lastSeenTimestamp: Long     // 마지막으로 발견된 시간 (업데이트용)
)

/**
 * 애니메이션 값을 담는 데이터 클래스
 */
data class AnimationValues(
    val buttonScale: Float,
    val buttonGlowAlpha: Float,
    val radarAngle: Float,
    val dotPulseScale: Float,
    val dotGlowAlpha: Float
)

/**
 * 리플 애니메이션 상태
 */
data class RippleState(
    val visible: Boolean,
    val animationValue: Float
)

// generateRandomName 함수는 더 이상 사용하지 않음 (닉네임은 BLE 광고에서 가져옴) 