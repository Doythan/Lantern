package com.ssafy.lanterns.ui.screens.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.lanterns.ui.theme.BleAccent
import com.ssafy.lanterns.ui.theme.BleBlue1
import com.ssafy.lanterns.ui.theme.BleBlue2
import com.ssafy.lanterns.ui.theme.BleDarkBlue
import com.ssafy.lanterns.ui.theme.BleGlow
import com.ssafy.lanterns.ui.theme.TextWhite
import com.ssafy.lanterns.ui.theme.TextWhite70
import com.ssafy.lanterns.utils.getConnectionColorByDistance
import kotlin.math.cos
import kotlin.math.sin

/**
 * 메인 화면의 중앙 컨텐츠
 */
@Composable
fun MainContent(
    @Suppress("UNUSED_PARAMETER") // 현재 직접 사용하지 않지만 향후 사용 예정
    isScanning: Boolean,
    onScanToggle: () -> Unit,
    nearbyPeople: List<NearbyPerson>,
    showPersonListModal: Boolean,
    onShowListToggle: () -> Unit,
    onPersonClick: (userId: String) -> Unit,
    rippleStates: Triple<RippleState, RippleState, RippleState>,
    animationValues: AnimationValues,
    buttonText: String,
    statusText: String,
    subTextVisible: Boolean,
    showListButton: Boolean
) {
    // 리플 애니메이션 상태
    val (ripple1, ripple2, ripple3) = rippleStates
    
    // 리플 파동 효과
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 첫 번째 리플
        if (ripple1.visible) {
            RippleCircle(
                scale = 1f + ripple1.animationValue * 3.0f,
                alpha = (1f - ripple1.animationValue) * 0.5f,
                color = BleBlue2.copy(alpha = 0.3f)  // 첫 번째 파동은 밝은 시안
            )
        }
        
        // 두 번째 리플
        if (ripple2.visible) {
            RippleCircle(
                scale = 1f + ripple2.animationValue * 3.0f,
                alpha = (1f - ripple2.animationValue) * 0.5f,
                color = BleBlue1.copy(alpha = 0.3f)  // 두 번째 파동은 깊은 파란색
            )
        }
        
        // 세 번째 리플
        if (ripple3.visible) {
            RippleCircle(
                scale = 1f + ripple3.animationValue * 3.0f,
                alpha = (1f - ripple3.animationValue) * 0.5f,
                color = BleAccent.copy(alpha = 0.2f)  // 세 번째 파동은 네온 민트
            )
        }
        
        // 주변 사람 점 표시
        nearbyPeople.forEach { person ->
            // 중앙 버튼의 크기(160dp)보다 최소 40dp 이상 떨어지도록 설정
            // 160dp/2 + 40dp = 120dp가 최소 반경
            val minRadius = 120.0 // 중앙 버튼과 겹치지 않는 최소 반경
            val maxRadius = 220.0 // 화면 내에 표시되는 최대 반경
            
            // 거리에 따라 반경 계산 (최소값 보장)
            val distanceRatio = person.distance / 500.0 // 거리 비율 조정
            val radius = minRadius + (maxRadius - minRadius) * distanceRatio.coerceIn(0.0, 1.0)
            
            val angleInRadians = Math.toRadians(person.angle.toDouble())
            val x = radius * cos(angleInRadians)
            val y = radius * sin(angleInRadians)
            
            Box(
                modifier = Modifier
                    .offset(x = x.dp, y = y.dp)
                    .size(50.dp), // 클릭 가능한 영역 확보 (크기 고정)
                contentAlignment = Alignment.Center
            ) {
                PersonDot(
                    modifier = Modifier,
                    signalStrength = person.signalStrength,
                    pulseScale = animationValues.dotPulseScale,
                    glowAlpha = animationValues.dotGlowAlpha,
                    distance = person.distance
                )
            }
        }
        
        // 중앙 스캔 버튼
        ScanButton(
            buttonScale = animationValues.buttonScale,
            buttonGlowAlpha = animationValues.buttonGlowAlpha,
            radarAngle = animationValues.radarAngle,
            buttonText = buttonText,
            onClick = onScanToggle
        )
        
        // 하단 정보 영역
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 상태 텍스트
            AnimatedVisibility(
                visible = subTextVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = statusText,
                    color = TextWhite70,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            
            // 목록 보기 버튼
            AnimatedVisibility(
                visible = showListButton,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                GradientButton(
                    text = "주변 사람 목록 보기",
                    onClick = onShowListToggle
                )
            }
        }
        
        // 사람 목록 모달
        if (showPersonListModal) {
            NearbyPersonListModal(
                people = nearbyPeople,
                onDismiss = onShowListToggle,
                onPersonClick = onPersonClick
            )
        }
    }
}

/**
 * 중앙 스캔 버튼
 */
@Composable
fun ScanButton(
    buttonScale: Float,
    buttonGlowAlpha: Float,
    radarAngle: Float,
    buttonText: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(160.dp)
            .scale(buttonScale)
            .shadow(elevation = 15.dp, shape = CircleShape, spotColor = BleGlow)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BleBlue2.copy(alpha = 0.9f), 
                        BleBlue1, 
                        BleDarkBlue
                    ),
                    radius = 180f
                )
            )
            .border(width = 1.5.dp, color = BleAccent.copy(alpha = 0.6f), shape = CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // 내부 빛나는 효과 원
        Box(
            modifier = Modifier
                .size(140.dp)
                .alpha(buttonGlowAlpha * 0.4f)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            BleAccent.copy(alpha = 0.7f),
                            BleBlue2.copy(alpha = 0.0f)
                        )
                    ),
                    shape = CircleShape
                )
        )
        
        // 레이더 스캔 라인 (회전하는 효과)
        Box(
            modifier = Modifier
                .size(150.dp)
                .drawBehind {
                    // 방사형 레이더 그리드 (고정)
                    for (i in 1..3) {
                        val radius = size.width * i / 8
                        drawCircle(
                            color = BleAccent.copy(alpha = 0.2f),
                            radius = radius,
                            style = Stroke(width = 1.5f)
                        )
                    }
                    
                    // 십자선 (고정)
                    drawLine(
                        color = BleAccent.copy(alpha = 0.15f),
                        start = Offset(center.x, 0f),
                        end = Offset(center.x, size.height),
                        strokeWidth = 1.5f
                    )
                    drawLine(
                        color = BleAccent.copy(alpha = 0.15f),
                        start = Offset(0f, center.y),
                        end = Offset(size.width, center.y),
                        strokeWidth = 1.5f
                    )
                    
                    // 회전하는 레이더 선
                    val angleRadians = Math.toRadians(radarAngle.toDouble())
                    val endX = center.x + (size.width / 2) * cos(angleRadians).toFloat()
                    val endY = center.y + (size.height / 2) * sin(angleRadians).toFloat()
                    
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                BleAccent.copy(alpha = 0.9f),
                                BleAccent.copy(alpha = 0.0f)
                            ),
                            start = center,
                            end = Offset(endX, endY)
                        ),
                        start = center,
                        end = Offset(endX, endY),
                        strokeWidth = 2.5f,
                        cap = StrokeCap.Round
                    )
                }
        )
        
        // 버튼 텍스트
        Text(
            text = buttonText,
            color = TextWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 리플 원형 효과
 */
@Composable
fun RippleCircle(
    scale: Float,
    alpha: Float,
    color: Color
) {
    Box(
        modifier = Modifier
            .size(140.dp)
            .scale(scale)
            .alpha(alpha)
            .border(
                width = 2.dp,
                color = color,
                shape = CircleShape
            )
    )
}

/**
 * 그라데이션 버튼
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        BleBlue1,
                        BleBlue2
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        BleAccent.copy(alpha = 0.4f),
                        BleAccent.copy(alpha = 0.1f)
                    )
                ),
                shape = CircleShape
            )
    ) {
        Text(
            text = text,
            color = TextWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
} 