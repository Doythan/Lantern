package com.ssafy.lanterns.ui.screens.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ssafy.lanterns.ui.theme.ConnectionFar
import com.ssafy.lanterns.ui.theme.ConnectionMedium
import com.ssafy.lanterns.ui.theme.ConnectionNear
import com.ssafy.lanterns.utils.getConnectionColorByDistance

/**
 * 주변 사람 표시 점 컴포넌트
 */
@Composable
fun PersonDot(
    modifier: Modifier = Modifier, 
    signalStrength: Float, 
    pulseScale: Float,
    glowAlpha: Float,
    distance: Float = (1f - signalStrength) * 10f // 신호 강도에 따른 가상 거리 계산
) {
    // 신호 강도에 따라 색상 결정
    val dotColor = getConnectionColorByDistance(distance)
    
    // 더 큰 크기로 설정 (기존 8dp → 12dp)
    val baseSize = 12.dp
    
    Box(
        modifier = modifier
    ) {
        // 빛나는 효과 (반짝이는 효과 강화)
        Box(
            modifier = Modifier
                .size(baseSize * 3.0f) // 발광 영역 확대
                .alpha(glowAlpha * signalStrength * 1.2f) // 밝기 약간 증가
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            dotColor.copy(alpha = 0.8f), // 중앙 색상 더 진하게
                            dotColor.copy(alpha = 0.0f)
                        )
                    ),
                    shape = CircleShape
                )
        )
        
        // 중앙 점 (크기 증가)
        Box(
            modifier = Modifier
                .size(baseSize)
                .align(Alignment.Center)
                .shadow(6.dp, CircleShape) // 그림자 강화
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, dotColor),
                        radius = baseSize.value * 0.8f
                    )
                )
                .border(1.0.dp, Color.White.copy(alpha = 0.9f), CircleShape) // 테두리 두껍게
        )
        
        // 반짝임 효과 강화
        Box(
            modifier = Modifier
                .size(baseSize * 2.0f) // 크기 증가
                .scale(pulseScale)
                .align(Alignment.Center)
                .alpha(0.6f * signalStrength) // 투명도 감소로 더 밝게
                .border(
                    width = 1.5.dp, // 테두리 두껍게
                    color = dotColor.copy(alpha = 0.8f), // 색상 더 진하게
                    shape = CircleShape
                )
        )
    }
} 