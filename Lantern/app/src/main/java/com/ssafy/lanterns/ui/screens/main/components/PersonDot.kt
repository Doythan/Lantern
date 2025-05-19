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
import com.ssafy.lanterns.utils.getConnectionColorBySignalLevel
import android.util.Log

/**
 * 주변 사람 표시 점 컴포넌트
 * @param signalLevel 신호 강도 레벨 (1: 약함, 2: 중간, 3: 강함)
 * @param depth 사용자의 Depth (거리 단계)
 * @param pulseScale 맥동 스케일 애니메이션 값
 * @param glowAlpha 발광 효과 알파값
 */
@Composable
fun PersonDot(
    modifier: Modifier = Modifier, 
    signalLevel: Int,
    depth: Int,
    pulseScale: Float,
    glowAlpha: Float
) {
    // 디버그 로그 추가
    Log.d("PersonDot", "도트 렌더링 시작: signalLevel=$signalLevel, depth=$depth")
    
    // 신호 레벨에 따라 색상 결정
    val dotColor = getConnectionColorBySignalLevel(signalLevel)
    
    // Depth에 따른 크기 스케일 계산 (작아질수록 더 멀리 있는 것)
    val depthScale = when {
        depth <= 1 -> 1.0f // 1홉 (직접 연결)
        depth == 2 -> 0.9f // 2홉 (약간 크게 수정)
        depth == 3 -> 0.8f // 3홉 (약간 크게 수정)
        depth <= 5 -> 0.7f // 4-5홉 (약간 크게 수정)
        else -> 0.6f // 6홉 이상 (약간 크게 수정)
    }
    
    // 기본 크기 증가 - 더 잘 보이도록
    val baseSize = 14.dp * depthScale
    
    // 알파값 계산 (더 멀수록 더 투명해짐) - 투명도 감소 (더 잘 보이도록)
    val depthAlpha = when {
        depth <= 1 -> 1.0f
        depth == 2 -> 0.95f
        depth == 3 -> 0.9f
        depth <= 5 -> 0.85f
        else -> 0.8f
    }
    
    Box(
        modifier = modifier
    ) {
        // 빛나는 효과 - 더 강하게
        Box(
            modifier = Modifier
                .size(baseSize * 3.5f) // 더 넓은 빛 효과
                .alpha(glowAlpha * depthAlpha * (signalLevel / 3f).coerceAtLeast(0.5f)) // 최소 알파값 보장
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            dotColor.copy(alpha = 0.9f * depthAlpha), // 더 강한 알파값
                            dotColor.copy(alpha = 0.1f) // 약간의 투명도는 유지
                        )
                    ),
                    shape = CircleShape
                )
        )
        
        // 중앙 점 - 더 선명하게
        Box(
            modifier = Modifier
                .size(baseSize)
                .align(Alignment.Center)
                .shadow(8.dp * depthScale, CircleShape, spotColor = dotColor) // 그림자 강화
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, dotColor),
                        radius = baseSize.value * 0.8f
                    )
                )
                .border(1.5.dp * depthScale, Color.White.copy(alpha = 0.95f * depthAlpha), CircleShape) // 테두리 두껍게
        )
        
        // 반짝임 효과 - 더 선명하게
        Box(
            modifier = Modifier
                .size(baseSize * 2.2f) // 약간 더 큰 반짝임
                .scale(pulseScale)
                .align(Alignment.Center)
                .alpha(0.7f * depthAlpha * (signalLevel / 3f).coerceAtLeast(0.5f)) // 최소 알파값 보장
                .border(
                    width = 2.0.dp * depthScale, // 테두리 두껍게
                    color = dotColor.copy(alpha = 0.9f * depthAlpha), // 더 강한 알파값
                    shape = CircleShape
                )
        )
    }
    
    // 디버그 로그 추가
    Log.d("PersonDot", "도트 렌더링 완료: baseSize=$baseSize, depthScale=$depthScale, depthAlpha=$depthAlpha")
} 