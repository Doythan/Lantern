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
    val baseSize = (6 + (signalStrength * 4)).dp
    
    Box(
        modifier = modifier
    ) {
        // 빛나는 효과 (원형 발광)
        Box(
            modifier = Modifier
                .size(baseSize * 3f)
                .alpha(glowAlpha * signalStrength)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            dotColor.copy(alpha = 0.8f),
                            dotColor.copy(alpha = 0.0f)
                        )
                    ),
                    shape = CircleShape
                )
        )
        
        // 중앙 점
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
                .border(0.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
        )
        
        // 맥동 효과 (펄싱)
        Box(
            modifier = Modifier
                .size(baseSize * 1.8f)
                .scale(pulseScale)
                .align(Alignment.Center)
                .alpha(0.3f * signalStrength)
                .border(
                    width = 1.dp,
                    color = dotColor,
                    shape = CircleShape
                )
        )
    }
} 