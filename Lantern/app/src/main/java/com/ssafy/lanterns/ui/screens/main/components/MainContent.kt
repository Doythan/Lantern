package com.ssafy.lanterns.ui.screens.main.components

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.lanterns.config.NeighborDiscoveryConstants
import com.ssafy.lanterns.ui.theme.BleAccent
import com.ssafy.lanterns.ui.theme.BleBlue1
import com.ssafy.lanterns.ui.theme.BleBlue2
import com.ssafy.lanterns.ui.theme.BleDarkBlue
import com.ssafy.lanterns.ui.theme.BluetoothColor
import com.ssafy.lanterns.ui.theme.BluetoothGlowColor
import com.ssafy.lanterns.ui.theme.TextWhite
import com.ssafy.lanterns.utils.getConnectionColorByDistance
import com.ssafy.lanterns.utils.getConnectionColorBySignalLevel
import kotlin.math.cos
import kotlin.math.sin
import com.ssafy.lanterns.ui.theme.LanternYellow
import com.ssafy.lanterns.ui.theme.LanternYellowDark
import com.ssafy.lanterns.ui.theme.DeepOrange
import com.ssafy.lanterns.ui.theme.MainScreenCardBg
import com.ssafy.lanterns.ui.theme.RadarEdgeColor
import com.ssafy.lanterns.ui.theme.RadarGradientEnd
import com.ssafy.lanterns.ui.theme.RadarGradientMiddle
import com.ssafy.lanterns.ui.theme.RadarGradientStart
import com.ssafy.lanterns.ui.theme.RadarLineColor

/**
 * 메인 화면의 중앙 컨텐츠
 */
@Composable
fun MainContent(
    isScanning: Boolean,
    nearbyPeopleToDisplay: List<NearbyPerson>,
    currentSelfDepth: Int,
    displayDepthLevel: Int,
    onDisplayDepthChange: (Int) -> Unit,
    showPersonListModal: Boolean,
    onShowListToggle: () -> Unit,
    onDismissModal: () -> Unit,
    onPersonClick: (serverUserIdString: String) -> Unit,
    onCallClick: (serverUserIdString: String) -> Unit,
    rippleStates: Triple<RippleState, RippleState, RippleState>,
    animationValues: AnimationValues,
    buttonText: String,
    subTextVisible: Boolean,
    showListButton: Boolean,
    onCheckBluetoothState: () -> Unit
) {
    // BoxWithConstraints를 통해 레이더 크기를 화면에 맞게 조정
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val maxSize = minOf(this.maxWidth, this.maxHeight) * 0.7f
        val radarSize = maxSize
        
        // 리플 애니메이션 상태
        val (ripple1, ripple2, ripple3) = rippleStates
        
        // 1. 리플 효과
        if (ripple1.visible) {
            RippleCircle(
                scale = 1f + ripple1.animationValue * 3.0f,
                alpha = (1f - ripple1.animationValue) * 0.5f,
                color = BleBlue2.copy(alpha = 0.3f)
            )
        }
        
        if (ripple2.visible) {
            RippleCircle(
                scale = 1f + ripple2.animationValue * 3.0f,
                alpha = (1f - ripple2.animationValue) * 0.5f,
                color = BleBlue1.copy(alpha = 0.3f)
            )
        }

        if (ripple3.visible) {
            RippleCircle(
                scale = 1f + ripple3.animationValue * 3.0f,
                alpha = (1f - ripple3.animationValue) * 0.5f,
                color = BleAccent.copy(alpha = 0.2f)
            )
        }
        
        // 2. 레이더 궤도 그리기
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = radarSize.toPx() / 2
            
            // 궤도 개수는 현재 displayDepthLevel에 따라 결정
            val orbitCount = displayDepthLevel
            
            // 각 궤도 그리기
            for (i in 1..orbitCount) {
                val orbitRadius = maxRadius * (i.toFloat() / orbitCount)
                
                // 궤도 선
                drawCircle(
                    color = RadarLineColor.copy(alpha = 0.15f),
                    radius = orbitRadius,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            
            // 레이더 스캔선 그리기 (animationValues.radarAngle 사용)
            val angle = Math.toRadians(animationValues.radarAngle.toDouble())
            val scanLineEndX = center.x + cos(angle).toFloat() * maxRadius
            val scanLineEndY = center.y + sin(angle).toFloat() * maxRadius
            
            drawLine(
                start = center,
                end = Offset(scanLineEndX, scanLineEndY),
                color = RadarLineColor.copy(alpha = 0.6f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        
        // 3. 주변 사람 점 표시
        nearbyPeopleToDisplay.forEach { person ->
            // 디버그 로그 추가
           // Log.d("MainContent", "PersonDot 렌더링: id=${person.serverUserIdString}, 닉네임=${person.nickname}, depth=${person.calculatedVisualDepth}, signalLevel=${person.signalLevel}")
            
            // 원형 레이더에서의 위치 계산 - 수정된 로직
            val depthRatio = person.calculatedVisualDepth.toFloat() / displayDepthLevel.coerceAtLeast(1)
            // 반지름 계산 수정 (최대 레이더 반지름의 비율로 계산)
            val radius = (radarSize.value / 2) * depthRatio.coerceIn(0.1f, 0.95f)
            
            val angleInRadians = Math.toRadians(person.angle.toDouble())
            val x = radius * cos(angleInRadians).toFloat()
            val y = radius * sin(angleInRadians).toFloat()
            
            // 로그 추가: 계산된 위치 정보
            // Log.d("MainContent", "도트 위치 계산: depthRatio=$depthRatio, radius=$radius, x=$x, y=$y")
            
            Box(
                modifier = Modifier
                    .offset(x = x.dp, y = y.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onPersonClick(person.serverUserIdString) }
                    .size(50.dp),
                contentAlignment = Alignment.Center
            ) {
                PersonDot(
                    signalLevel = person.signalLevel,
                    depth = person.calculatedVisualDepth,
                    pulseScale = animationValues.dotPulseScale,
                    glowAlpha = animationValues.dotGlowAlpha
                )
            }
        }
        
        // 4. 중앙 스캔 버튼
        Box(
            modifier = Modifier.align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            // 외부 글로우 효과
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(animationValues.buttonScale)
                    .alpha(animationValues.buttonGlowAlpha * 0.6f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(DeepOrange.copy(alpha = 0.3f), Color.Transparent),
                            radius = 200f * 0.75f
                        )
                    )
            )
            
            // 중앙 버튼
            LanternCenterButton(
                buttonScale = animationValues.buttonScale,
                buttonGlowAlpha = animationValues.buttonGlowAlpha,
                radarAngle = animationValues.radarAngle,
                buttonText = buttonText,
                subTextVisible = subTextVisible,
                onScanToggle = {}
            )
        }
        
        // 5. 상단 랜턴 개수 및 내 Depth 표시
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NearbyLanternCount(count = nearbyPeopleToDisplay.size, selfDepth = currentSelfDepth)
        }
        
        // 6. 하단 UI (목록 보기 버튼)
        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 항상 보이는 '주변 목록 보기' 버튼 (AnimatedVisibility 제거)
            GradientButton(
                text = "주변 목록 보기",
                onClick = onShowListToggle
            )
        }
        
        // 7. 사람 목록 모달
        if (showPersonListModal) {
            NearbyPersonListModal(
                people = nearbyPeopleToDisplay,
                onDismiss = onDismissModal,
                onPersonClick = onPersonClick,
                onCallClick = onCallClick
            )
        }

        // 디버그용 블루투스 상태 확인 버튼 추가 (레이더 위에 배치)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopEnd
        ) {
            TextButton(
                onClick = onCheckBluetoothState,
                modifier = Modifier.padding(top = 8.dp, end = 8.dp)
            ) {
                Text("BLE 상태 확인", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

/**
 * 주변 랜턴 개수를 표시하는 컴포넌트
 */
@Composable
fun NearbyLanternCount(count: Int, selfDepth: Int) {
    Row(
        modifier = Modifier
            .background(MainScreenCardBg.copy(alpha = 0.8f), CircleShape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "주변 랜턴: $count",
            color = TextWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "·",
            color = TextWhite,
            fontSize = 16.sp
        )
        Text(
            text = "내 홉수: $selfDepth",
            color = TextWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 중앙 랜턴 버튼 (클릭 기능 없음)
 */
@Composable
fun LanternCenterButton(
    buttonScale: Float,
    buttonGlowAlpha: Float,
    radarAngle: Float,
    buttonText: String,
    subTextVisible: Boolean,
    onScanToggle: () -> Unit
) {
    val baseColor = LanternYellow
    val darkColor = LanternYellowDark
    val highlightColor = DeepOrange // Color(0xFFE65100) -> DeepOrange
    
    // MaterialTheme 값을 블록 외부에서 변수로 미리 가져옴
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    
    // 블루투스 펄스 애니메이션
    val bluetoothPulse = rememberInfiniteTransition(label = "bluetoothPulse")
    val bluetoothAlpha by bluetoothPulse.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing)
        ),
        label = "bluetoothAlpha"
    )
    
    // 블루투스 스케일 애니메이션
    val bluetoothScale by bluetoothPulse.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing)
        ),
        label = "bluetoothScale"
    )

    Box(
        modifier = Modifier
            .size(160.dp)
            .scale(buttonScale)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(baseColor, darkColor),
                    radius = 150f
                )
            )
            .border(2.dp, BleDarkBlue, CircleShape) // BleDarkBlue is from Color.kt
            .padding(8.dp)
            .drawBehind {
                // 레이더 스캔 라인 (회전하는 효과)
                for (i in 1..3) {
                    val radius = size.width * i / 8
                    drawCircle(
                        color = RadarLineColor, // 테마 색상 사용
                        radius = radius,
                        style = Stroke(width = 2.0f) // 선 두께 조금 줄임
                    )
                }
                
                // 십자선 (고정)
                drawLine(
                    color = RadarLineColor, // 테마 색상 사용
                    start = Offset(center.x, 0f),
                    end = Offset(center.x, size.height),
                    strokeWidth = 2.0f // 선 두께 조금 줄임
                )
                drawLine(
                    color = RadarLineColor, // 테마 색상 사용
                    start = Offset(0f, center.y),
                    end = Offset(size.width, center.y),
                    strokeWidth = 2.0f // 선 두께 조금 줄임
                )
                
                // 레이더 스캔 부채꼴 (회전하는 영역) - 선 대신 부채꼴 영역으로 변경
                val sweepAngle = 60f // 부채꼴 각도
                val startAngle = radarAngle - sweepAngle / 2
                
                // 레이더 부채꼴 - 투명한 영역
                drawArc(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            RadarGradientStart, // 테마 색상 사용 - 더 투명한 흰색
                            RadarGradientMiddle, // 테마 색상 사용 - 더 투명한 흰색
                            RadarGradientEnd // 테마 색상 사용 - 완전 투명
                        )
                    ),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = true,
                    size = size
                )
                
                // 레이더 부채꼴 테두리
                drawArc(
                    color = RadarEdgeColor, // 테마 색상 사용 - 테두리 투명도 높임
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = true,
                    style = Stroke(width = 1.5f), // 테두리 두께 조금 줄임
                    size = size
                )
                
                // 부채꼴 앞쪽 에지에 밝은 선 추가
                val angleRadians = Math.toRadians(radarAngle.toDouble())
                val endX = center.x + (size.width / 2) * cos(angleRadians).toFloat()
                val endY = center.y + (size.height / 2) * sin(angleRadians).toFloat()
                
                // 선명한 에지 선
                drawLine(
                    color = Color.White.copy(alpha = 0.7f), // 에지는 조금 선명하게 유지
                    start = center,
                    end = Offset(endX, endY),
                    strokeWidth = 2.0f, // 선 두께 조금 줄임
                    cap = StrokeCap.Round
                )
            },
        contentAlignment = Alignment.Center // 중앙 정렬 추가
    ) {
        // 블루투스 아이콘 추가
        Icon(
            imageVector = Icons.Filled.Bluetooth,
            contentDescription = "블루투스 연결",
            tint = BluetoothColor.copy(alpha = bluetoothAlpha), // 테마 색상 사용
            modifier = Modifier
                .size(45.dp) // 아이콘 크기 유지
                .scale(bluetoothScale) // 스케일 애니메이션 유지
                .shadow(elevation = 10.dp, spotColor = BluetoothGlowColor, shape = CircleShape) // 테마 색상 사용
        )
    }
    
    // Radar Glow (outer glow) 부분은 중앙 배치를 위해 상위 Box로 이동시킴
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
    // 랜턴 빛이 퍼져나가는 느낌의 파동 - 흰색 계열로 변경
    Box(
        modifier = Modifier
            .size(140.dp)
            .scale(scale)
            .alpha(alpha)
            .drawBehind {
                // 랜턴 빛 효과를 내기 위한 그라데이션 원 (흰색 기반)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.4f), // 흰색으로 변경
                            Color.White.copy(alpha = 0.2f),
                            Color.White.copy(alpha = 0.0f)
                        ),
                        radius = size.width * 0.5f
                    ),
                    radius = size.width * 0.5f
                )
                
                // 테두리 - 흰색 계열로 변경하고 더 두껍게
                drawCircle(
                    color = Color.White.copy(alpha = 0.7f), // 흰색으로 변경
                    radius = size.width * 0.5f,
                    style = Stroke(width = 4.0f) // 더 두껍게
                )
            }
    )
}

/**
 * 랜턴 형태의 점 (기존 PersonDot 대체)
 */
@Composable
fun PersonDot(
    signalLevel: Int,
    depth: Float,
    pulseScale: Float,
    glowAlpha: Float
) {
    val connectionColor = getConnectionColorBySignalLevel(signalLevel)
    val lanternYellow = Color(0xFFFFC107) // 랜턴 노란색
    
    Box(
        modifier = Modifier
            .size(14.dp) // 기본 크기 유지
            .scale(pulseScale)
            .shadow(
                elevation = 8.dp,
                spotColor = lanternYellow,
                shape = CircleShape
            )
            .clip(CircleShape)
            .background(lanternYellow) // 노란색 랜턴 색상으로 변경
    ) {
        // 내부 빛 효과
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(glowAlpha)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            lanternYellow.copy(alpha = 0.7f)
                        )
                    )
                )
        )
        
        // 빛 테두리 효과
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // 빛이 퍼지는 효과를 위한 외부 원
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                lanternYellow.copy(alpha = 0.6f),
                                lanternYellow.copy(alpha = 0.0f)
                            ),
                            radius = size.width * 1.2f
                        ),
                        radius = size.width * 1.2f
                    )
                }
        )
    }
}

/**
 * 그라데이션 버튼 - 노란색 계열(랜턴색)으로 변경 및 크기 유지
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit
) {
    // 랜턴 색상 정의 - 노란색 계열로 변경
    val lanternColor = Color(0xFFFFC107) // 밝은 노란색
    val lanternColorDark = Color(0xFFFF9800) // 어두운 노란색

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .fillMaxWidth(0.8f) // 너비 유지
            .height(56.dp) // 고정 높이 유지
            .clip(CircleShape)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        lanternColor, // 밝은 노란색
                        lanternColorDark // 어두운 노란색
                    )
                )
            )
            .border(
                width = 2.dp, // 테두리 두께 유지
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.5f), // 테두리도 밝게
                        Color.White.copy(alpha = 0.2f)
                    )
                ),
                shape = CircleShape
            )
    ) {
        Text(
            text = text,
            color = Color.White, // 검정색에서 흰색으로 변경
            fontSize = 17.sp, // 폰트 크기 유지
            fontWeight = FontWeight.Bold, // 폰트 두께 증가
            modifier = Modifier.padding(horizontal = 30.dp, vertical = 12.dp) // 패딩 유지
        )
    }
} 