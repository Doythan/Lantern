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
    
    // 더 작은 고정 크기 설정
    val baseSize = 8.dp
    
    Box(
        modifier = modifier
    ) {
        // 빛나는 효과 (반짝이는 효과만 유지)
        Box(
            modifier = Modifier
                .size(baseSize * 2.5f)
                .alpha(glowAlpha * signalStrength)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            dotColor.copy(alpha = 0.7f),
                            dotColor.copy(alpha = 0.0f)
                        )
                    ),
                    shape = CircleShape
                )
        )
        
        // 중앙 점 (크기 축소)
        Box(
            modifier = Modifier
                .size(baseSize)
                .align(Alignment.Center)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, dotColor),
                        radius = baseSize.value * 0.8f
                    )
                )
                .border(0.5.dp, Color.White.copy(alpha = 0.9f), CircleShape)
        )
        
        // 단순한 반짝임 효과 (기존 복잡한 맥동 효과 대체)
        Box(
            modifier = Modifier
                .size(baseSize * 1.6f)
                .scale(pulseScale)
                .align(Alignment.Center)
                .alpha(0.5f * signalStrength)
                .border(
                    width = 1.1.dp,
                    color = dotColor.copy(alpha = 0.7f),
                    shape = CircleShape
                )
        )
    }
} 