package com.ssafy.lanterns.ui.screens.main.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.lanterns.ui.theme.BluetoothColor
import com.ssafy.lanterns.ui.theme.BluetoothGlowColor
import com.ssafy.lanterns.ui.theme.LanternYellow
import com.ssafy.lanterns.ui.theme.RadarLineColor
import com.ssafy.lanterns.ui.util.getProfileImageByNumber
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 메인 화면의 중앙 컨텐츠
 */
@Composable
fun MainContent(
    nearbyPeopleToDisplay: List<NearbyPerson>,
    showPersonListModal: Boolean,
    onDismissModal: () -> Unit,
    onPersonClick: (serverUserIdString: String) -> Unit,
    onCallClick: (serverUserIdString: String) -> Unit,
    rippleStates: Triple<RippleState, RippleState, RippleState>,
    animationValues: AnimationValues,
    userProfileImageNumber: Int = 0 // 사용자 프로필 이미지 번호 매개변수 추가 (기본값 0)
) {
    // 애니메이션 값 추출 - 실제 사용되는 값만 유지
    val buttonScale = animationValues.buttonScale
    
    // 줌 상태 관리
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    // 자동 줌을 위한 상태
    var needsZoomAdjustment by remember { mutableStateOf(false) }
    var targetScale by remember { mutableFloatStateOf(1f) }
    var targetOffset by remember { mutableStateOf(Offset.Zero) }
    
    // 주변 사용자 수에 따른 배경 밝기 계산 - 더 밝은 효과를 위해 개선
    val backgroundBrightness by remember(nearbyPeopleToDisplay.size) {
        derivedStateOf {
            // 사용자 수가 많을수록 배경이 밝아지는 효과 (0.05f ~ 0.4f)로 범위 확장
            val minBrightness = 0.05f
            val maxBrightness = 0.4f
            // 기준 인원수를 5명으로 낮춰 적은 인원 변화에도 효과가 보이도록 함
            val userCountFactor = (nearbyPeopleToDisplay.size / 5f).coerceIn(0f, 1f)
            minBrightness + (maxBrightness - minBrightness) * userCountFactor
        }
    }
    
    // 사용자 ID별 랜덤 각도와 마지막 RSSI 값을 저장하는 맵
    val userAngles = remember { mutableStateMapOf<String, Float>() }
    val lastRssiValues = remember { mutableStateMapOf<String, Int>() }
    val userDistanceFactors = remember { mutableStateMapOf<String, Float>() } // 중앙 집중도를 위한 거리 계수
    
    // 새로운 사용자 감지를 위한 ID 집합
    val knownUserIds = remember { mutableStateOf(emptySet<String>()) }
    
    // 사용자별 애니메이션 스케일 값 저장 (새 사용자 효과)
    val userAnimationScales = remember { mutableStateMapOf<String, Animatable<Float, *>>() }

    // 도트 위치 충돌 방지를 위한 맵 - 기존 배치된 점들의 위치 저장
    val existingDotPositions = remember { mutableStateMapOf<String, Pair<Float, Float>>() }

    // 화면 바깥 프로필 여부 체크를 위한 함수
    fun checkProfilesOutsideVisibleArea(
        radarSize: androidx.compose.ui.unit.Dp,
        profiles: List<NearbyPerson>,
        currentScale: Float,
        currentOffset: Offset
    ): Boolean {
        if (profiles.isEmpty()) return false
        
        val maxVisibleRadius = radarSize.value / 2 * currentScale
        
        profiles.forEach { person ->
            val centralFactor = userDistanceFactors[person.serverUserIdString] ?: 0.5f
            val rssiNormalized = ((person.rssi + 100) / 70f).coerceIn(0.1f, 0.95f)
            val radius = (radarSize.value / 2) * centralFactor * (1 - rssiNormalized * 0.3f)
            
            val randomAngle = userAngles[person.serverUserIdString] ?: 0f
            val angleInRadians = Math.toRadians(randomAngle.toDouble())
            val x = radius * cos(angleInRadians).toFloat() + currentOffset.x
            val y = radius * sin(angleInRadians).toFloat() + currentOffset.y
            
            // 화면 바깥에 있는지 거리 계산
            val distanceFromCenter = sqrt(x * x + y * y)
            if (distanceFromCenter > maxVisibleRadius * 0.85f) {
                return true
            }
        }
        
        return false
    }
    
    // 줌 제스처 상태 - 중앙 기준으로 줌 되도록 수정
    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        // 줌 스케일 제한 (0.4f ~ 2.0f)
        scale = (scale * zoomChange).coerceIn(0.4f, 2.0f)
        
        // 오프셋 계산 - 범위 제한 완화
        offset = Offset(
            (offset.x + offsetChange.x).coerceIn(-200f, 200f),
            (offset.y + offsetChange.y).coerceIn(-200f, 200f)
        )
    }
    
    // BoxWithConstraints를 통해 레이더 크기를 화면에 맞게 조정
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // maxSize를 정사각형으로 만들기 위해 width와 height 중 작은 값 사용 (타원형 방지)
        val maxSize = minOf(this.maxWidth, this.maxHeight)
        val radarSize = maxSize * 0.9f // 화면에 맞게 크기 조정
        
        // 화면 바깥 프로필 체크 및 자동 줌 조정
        LaunchedEffect(nearbyPeopleToDisplay, scale, offset) {
            // 일정 간격으로 프로필 위치 체크
            delay(1000)
            
            // 사용자 상호작용 중에는 자동 줌 조정 안 함
            if (!transformState.isTransformInProgress) {
                val hasOutsideProfiles = checkProfilesOutsideVisibleArea(radarSize, nearbyPeopleToDisplay, scale, offset)
                
                if (hasOutsideProfiles && scale > 0.5f) {
                    // 화면 바깥에 프로필이 있고 현재 줌인 상태이면 줌아웃
                    targetScale = (scale * 0.85f).coerceAtLeast(0.5f)
                    targetOffset = Offset(offset.x * 0.85f, offset.y * 0.85f)
                    needsZoomAdjustment = true
                } else if (!hasOutsideProfiles && nearbyPeopleToDisplay.size <= 3 && scale < 1.5f) {
                    // 화면 바깥에 프로필이 없고 적은 수의 프로필이면 줌인
                    targetScale = (scale * 1.15f).coerceAtMost(1.5f)
                    needsZoomAdjustment = true
                }
            }
        }
        
        // 자동 줌 조정 LaunchedEffect
        LaunchedEffect(needsZoomAdjustment) {
            if (needsZoomAdjustment) {
                // 부드러운 애니메이션으로 줌 조정
                val startScale = scale
                val startOffset = offset
                val animDuration = 500 // 애니메이션 시간 (ms)
                
                for (i in 1..30) { // 30프레임 애니메이션
                    val fraction = i / 30f
                    // 이징 함수 적용 (easeInOutQuad)
                    val easedFraction = if (fraction < 0.5) 2 * fraction * fraction else -1 + (4 - 2 * fraction) * fraction
                    
                    scale = startScale + (targetScale - startScale) * easedFraction
                    offset = Offset(
                        startOffset.x + (targetOffset.x - startOffset.x) * easedFraction,
                        startOffset.y + (targetOffset.y - startOffset.y) * easedFraction
                    )
                    
                    delay(animDuration / 30L) // 부드러운 애니메이션 위한 딜레이
                }
                
                // 값 확실히 적용
                scale = targetScale
                offset = targetOffset
                needsZoomAdjustment = false
            }
        }
        
        // 현재 및 이전 사용자 ID 세트 계산
        LaunchedEffect(nearbyPeopleToDisplay) {
            val currentUserIds = nearbyPeopleToDisplay.map { it.serverUserIdString }.toSet()
            val newUserIds = currentUserIds - knownUserIds.value
            
            // 새 사용자 ID들에 대해 애니메이션 객체 생성
            newUserIds.forEach { userId ->
                userAnimationScales[userId] = Animatable(1.5f)
            }
            
            // 각 새 사용자의 애니메이션 시작
            newUserIds.forEach { userId ->
                userAnimationScales[userId]?.let { animatable ->
                    animatable.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = 0.3f,
                            stiffness = 300f
                        )
                    )
                }
            }
            
            // 각 사용자의 RSSI 값 변화 확인 및 위치 업데이트
            nearbyPeopleToDisplay.forEach { person ->
                val userId = person.serverUserIdString
                val lastRssi = lastRssiValues[userId]
                
                // RSSI 값이 크게 변경되었거나 새로운 사용자인 경우 새로운 랜덤 각도 할당
                if (lastRssi == null || abs(lastRssi - person.rssi) > 10) {
                    // 각도는 여전히 전체 범위를 사용하지만 시각적으로 상단(텍스트 있는 부분)을 피함
                    val newAngle = Random.nextFloat() * 360f
                    userAngles[userId] = newAngle
                    
                    // 중앙 주변에 더 많이 배치되도록 거리 계수 설정 (0.3~0.8 범위로 제한)
                    // 값이 작을수록 중앙에 가까워짐
                    val centralFactor = 0.3f + Random.nextFloat() * 0.5f
                    userDistanceFactors[userId] = centralFactor
                    
                    lastRssiValues[userId] = person.rssi
                }
            }
            
            // 도트 위치 충돌 방지 맵 초기화
            existingDotPositions.clear()
            
            // 사라진 사용자의 데이터 정리
            val removedUserIds = knownUserIds.value - currentUserIds
            removedUserIds.forEach { userId ->
                userAngles.remove(userId)
                lastRssiValues.remove(userId)
                userAnimationScales.remove(userId)
                userDistanceFactors.remove(userId)
                existingDotPositions.remove(userId)
            }
            
            // 알고 있는 사용자 ID 업데이트
            knownUserIds.value = currentUserIds
        }
        
        // 리플 애니메이션 상태
        // Triple 값을 개별 변수로 분해하지 않고 바로 사용
        // val (ripple1, ripple2, ripple3) = rippleStates
        
        // 1. 상단 텍스트 추가 (주변에 XX명의 랜턴이 있습니다)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "주변에 ",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${nearbyPeopleToDisplay.size}",
                    color = LanternYellow, // 노란색으로 강조
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "명의 ",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "랜턴",
                    color = LanternYellow, // 노란색으로 강조
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "이 있습니다",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // 줌/패닝이 가능한 컨테이너
        Box(
            modifier = Modifier
                .fillMaxSize()
                .transformable(state = transformState),
            contentAlignment = Alignment.Center
        ) {
            // 확장된 레이더 영역 (정사각형 유지)
            Box(
                modifier = Modifier
                    .size(radarSize) // 정사각형 크기로 설정
                    .scale(scale)
                    .offset { IntOffset(offset.x.toInt(), offset.y.toInt()) },
                contentAlignment = Alignment.Center
            ) {
                // 2. 리플 효과 (개선된 펄스 효과) - 중복 코드 제거하고 하나의 함수로 처리
                rippleStates.toList().forEach { rippleState ->
                    if (rippleState.visible) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            RippleCircle(
                                scale = 1f + rippleState.animationValue * 3.0f,
                                alpha = (1f - rippleState.animationValue) * 0.5f
                            )
                        }
                    }
                }
                
                // 3. 레이더 원형 배경 (사용자 수에 따라 밝기 조절)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = backgroundBrightness),
                                    Color.Transparent
                                ),
                                radius = radarSize.value / 2f
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                )
                
                // 4. 레이더 궤도 그리기
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val maxRadius = minOf(size.width, size.height) / 2
                    
                    // 궤도 개수를 7개로 늘리고 화면 바깥까지 확장
                    val orbitCount = 7
                    
                    // 각 궤도 그리기
                    for (i in 1..orbitCount) {
                        // 모든 원을 균등한 간격으로 배치
                        val orbitRadius = maxRadius * (i.toFloat() / 5)
                        
                        // 궤도 선
                        drawCircle(
                            color = RadarLineColor.copy(alpha = 0.15f),
                            radius = orbitRadius,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }
                
                // 5. 중앙 BLE 아이콘으로 변경
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(60.dp)
                        .scale(buttonScale)
                        .drawBehind {
                            // 주변 빛나는 효과
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        BluetoothGlowColor.copy(alpha = 0.5f),
                                        BluetoothGlowColor.copy(alpha = 0.2f),
                                        Color.Transparent
                                    )
                                ),
                                radius = size.width * 0.8f
                            )
                        }
                        .clip(CircleShape)
                        .background(Color.DarkGray.copy(alpha = 0.7f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // 블루투스 아이콘 애니메이션
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
                    
                    // 블루투스 아이콘
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = "블루투스 연결",
                        tint = BluetoothColor.copy(alpha = bluetoothAlpha),
                        modifier = Modifier
                            .size(35.dp)
                            .scale(bluetoothScale)
                            .shadow(elevation = 10.dp, spotColor = BluetoothGlowColor, shape = CircleShape)
                    )
                }
                
                // 6. 주변 사람 프로필 이미지 표시 - 개선된 프로필 크기와 충돌 방지 로직
                nearbyPeopleToDisplay.forEach { person ->
                    // RSSI 기반으로만 위치 계산
                    val rssiNormalized = ((person.rssi + 100) / 70f).coerceIn(0.1f, 0.95f)
                    
                    // 중앙 집중도를 적용한 반지름 계산
                    val centralFactor = userDistanceFactors[person.serverUserIdString] ?: 0.5f
                    val radius = (radarSize.value / 2) * centralFactor * (1 - rssiNormalized * 0.3f)
                    
                    // 랜덤 각도 사용
                    val randomAngle = userAngles[person.serverUserIdString] ?: run {
                        val newAngle = Random.nextFloat() * 360f
                        userAngles[person.serverUserIdString] = newAngle
                        newAngle
                    }
                    
                    // 기본 위치 계산
                    val angleInRadians = Math.toRadians(randomAngle.toDouble())
                    var x = radius * cos(angleInRadians).toFloat()
                    var y = radius * sin(angleInRadians).toFloat()
                    
                    // 프로필 이미지 크기 계산 (RSSI에 따라 더 명확한 차이)
                    val profileSize = when {
                        person.rssi >= -60 -> 35.dp  // 매우 가까울 때 (큰 크기)
                        person.rssi >= -75 -> 30.dp  // 가까울 때 (중간 크기)
                        person.rssi >= -85 -> 25.dp  // 중간 거리
                        else -> 20.dp               // 멀리 있을 때 (작은 크기)
                    }
                    
                    // RSSI에 따른 빛 강도 계산
                    val glowIntensity = when {
                        person.rssi > -60 -> 0.8f  // 매우 강한 신호
                        person.rssi > -75 -> 0.6f  // 강한 신호
                        person.rssi > -85 -> 0.4f  // 중간 신호
                        else -> 0.2f              // 약한 신호
                    }
                    
                    // 새 사용자 효과를 위한 스케일 애니메이션 적용
                    val profileScale = userAnimationScales[person.serverUserIdString]?.value ?: 1f
                    
                    // 도트 충돌 방지 로직 (이미 배치된 다른 도트와 충돌하는지 확인하고 위치 조정)
                    if (!existingDotPositions.containsKey(person.serverUserIdString)) {
                        var attempts = 0
                        // 변수 선언 시 초기화 없이, 필요한 시점에 할당
                        var needsRepositioning: Boolean
                        val minDistanceBetweenDots = profileSize.value * 1.2f // 최소 거리(프로필 크기의 1.2배)
                        
                        do {
                            needsRepositioning = false
                            // 모든 기존 배치된 도트와의 거리 확인
                            for ((_, position) in existingDotPositions) {
                                val (existingX, existingY) = position
                                val distance = sqrt((x - existingX).pow(2) + (y - existingY).pow(2))
                                
                                if (distance < minDistanceBetweenDots) {
                                    // 겹치면 위치 조정 필요
                                    needsRepositioning = true
                                    // 새 랜덤 각도로 조정 (기존 각도에서 조금 벗어남)
                                    val adjustedAngle = randomAngle + (10f * (attempts + 1) * (if (Random.nextBoolean()) 1 else -1))
                                    val newRadians = Math.toRadians(adjustedAngle.toDouble())
                                    // 거리도 약간 조정 (중심에서 더 멀어지거나 가까워짐)
                                    val adjustment = 1f + (0.1f * (attempts + 1) * (if (Random.nextBoolean()) 1 else -1))
                                    val adjustedRadius = radius * adjustment.coerceIn(0.6f, 1.4f)
                                    
                                    x = adjustedRadius * cos(newRadians).toFloat()
                                    y = adjustedRadius * sin(newRadians).toFloat()
                                    break
                                }
                            }
                            attempts++
                        } while (needsRepositioning && attempts < 5) // 최대 5번 시도
                        
                        // 최종 결정된 위치 저장
                        existingDotPositions[person.serverUserIdString] = Pair(x, y)
                    } else {
                        // 이미 위치가 결정된 경우 해당 위치 사용
                        val (storedX, storedY) = existingDotPositions[person.serverUserIdString]!!
                        x = storedX
                        y = storedY
                    }
                    
                    Box(
                        modifier = Modifier
                            .offset(x = x.dp, y = y.dp)
                            .scale(profileScale) // 새 사용자 애니메이션 효과 적용
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onPersonClick(person.serverUserIdString) },
                        contentAlignment = Alignment.Center
                    ) {
                        // 외부 발광 효과
                        Box(
                            modifier = Modifier
                                .size(profileSize * 1.5f)
                                .alpha(glowIntensity)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            LanternYellow.copy(alpha = 0.7f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = CircleShape
                                )
                        )
                        
                        // 프로필 이미지 (원형)
                        Box(
                            modifier = Modifier
                                .size(profileSize)
                                .clip(CircleShape)
                                .background(LanternYellow)
                                .border(1.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            // 프로필 이미지 번호 결정
                            val imageNumber = if (person.profileImageNumber > 0) {
                                // 저장된 프로필 이미지 번호가 있으면 그 번호 사용
                                person.profileImageNumber
                            } else {
                                // 없으면 서버ID 기반으로 랜덤 결정 (1-6 범위)
                                (abs(person.serverUserIdString.hashCode()) % 6) + 1
                            }
                            
                            // 이미지 리소스 ID 가져오기
                            val imageResId = getProfileImageByNumber(imageNumber)
                            
                            // 실제 이미지 리소스 표시
                            Image(
                                painter = painterResource(id = imageResId),
                                contentDescription = "프로필 이미지 $imageNumber",
                                modifier = Modifier
                                    .size(profileSize)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                        
                        // 닉네임 표시 - 여백 증가
                        Text(
                            text = person.nickname.take(5),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = profileSize + 12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
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
    }
}

/**
 * 리플 원형 효과 - 최적화된 버전
 */
@Composable
fun RippleCircle(
    scale: Float,
    alpha: Float
) {
    // 펄스 애니메이션 (미세한 진동 효과)
    val pulseAnim = rememberInfiniteTransition(label = "pulseAnim")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    // 파티클 빛 효과 애니메이션
    val glowAnim = rememberInfiniteTransition(label = "glowAnim")
    val glowAlpha by glowAnim.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    // 랜턴 빛이 퍼져나가는 느낌의 파동
    Box(
        modifier = Modifier
            .size(140.dp)
            .scale(scale * pulseScale)
            .alpha(alpha)
            .drawBehind {
                val minSize = minOf(size.width, size.height)
                val radius = minSize * 0.5f
                val center = Offset(size.width / 2, size.height / 2)
                
                // 랜턴 빛 효과를 내기 위한 그라데이션 원
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.4f * glowAlpha),
                            Color.White.copy(alpha = 0.2f * glowAlpha),
                            Color.White.copy(alpha = 0.0f)
                        ),
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
                
                // 테두리
                drawCircle(
                    color = Color.White.copy(alpha = 0.7f * glowAlpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 4.0f)
                )
                
                // 작은 파티클 효과 추가
                val particleCount = 8
                val particleRadius = 2.0f
                
                for (i in 0 until particleCount) {
                    val angle = (i * 360f / particleCount) + (glowAlpha * 120f)
                    val distance = radius * 0.8f
                    
                    val x = center.x + distance * cos(Math.toRadians(angle.toDouble())).toFloat()
                    val y = center.y + distance * sin(Math.toRadians(angle.toDouble())).toFloat()
                    
                    // 작은 발광 입자
                    drawCircle(
                        color = Color.White.copy(alpha = glowAlpha),
                        radius = particleRadius,
                        center = Offset(x, y)
                    )
                }
            }
    )
} 