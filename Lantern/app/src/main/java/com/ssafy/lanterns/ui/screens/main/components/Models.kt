package com.ssafy.lanterns.ui.screens.main.components

import kotlin.random.Random

/**
 * 주변 사람 데이터 모델
 */
data class NearbyPerson(
    val id: Int,
    val userId: String = id.toString(), // 사용자 고유 ID 추가
    val distance: Float, // 거리 (미터 단위)
    val angle: Float,    // 시계 방향으로 각도 (0-360도)
    val signalStrength: Float, // 신호 강도 (0.0-1.0)
    val name: String = generateRandomName(), // 랜덤 이름
    val avatarSeed: Int = Random.nextInt(100) // 아바타 생성용 시드
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

/**
 * 랜덤 이름 생성 함수
 */
fun generateRandomName(): String {
    val firstNames = listOf("김", "이", "박", "최", "정", "강", "조", "윤", "장", "임")
    val lastNames = listOf("민준", "지훈", "준호", "도윤", "서연", "지은", "하은", "수빈", "예은", "민서")
    return "${firstNames.random()}${lastNames.random()}"
} 