package com.ssafy.lanterns.ui.screens.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ssafy.lanterns.ui.screens.main.components.AnimationValues
import com.ssafy.lanterns.ui.screens.main.components.MainContent
import com.ssafy.lanterns.ui.screens.main.components.RippleState
import com.ssafy.lanterns.ui.screens.ondevice.OnDeviceAIDialog
import com.ssafy.lanterns.ui.screens.ondevice.OnDeviceAIViewModel
import com.ssafy.lanterns.ui.theme.LanternsTheme
import com.ssafy.lanterns.ui.theme.NavyBottom
import com.ssafy.lanterns.ui.theme.NavyTop
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.content.Context
import com.ssafy.lanterns.ui.navigation.AppDestinations

/**
 * 메인 화면
 * 그라데이션 배경 및 BLE 스캔 기능을 제공하는 화면
 * 
 * BLE 광고/스캔 기능 구현 가이드:
 * 1. 시작하기 버튼을 클릭할 때 BLE 광고 및 스캔 기능이 활성화됩니다.
 * 2. 다시 버튼을 클릭하면 BLE 광고 및 스캔 기능이 비활성화됩니다.
 * 3. BLE 기능은 ViewModel에서 관리하며, 필요한 권한 및 블루투스 상태를 확인합니다.
 */
@Composable
fun MainScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(0.dp),
    viewModel: MainViewModel = hiltViewModel()
) {
    // ViewModel로부터 UI 상태 수집
    val uiState by viewModel.uiState.collectAsState()
    
    // 코루틴 스코프
    val coroutineScope = rememberCoroutineScope()
    
    // 리플 애니메이션 관련 상태
    val rippleVisible = remember { mutableStateOf(false) }
    val rippleAnimatable1 = remember { Animatable(0f) }
    val rippleAnimatable2 = remember { Animatable(0f) }
    val rippleAnimatable3 = remember { Animatable(0f) }
    
    // 생명주기 관찰자 - 업데이트된 패키지 사용
    val lifecycleOwner = LocalLifecycleOwner.current
    // TODO: context 변수는 BLE 로직 구현 시 사용될 예정입니다.
    @Suppress("UNUSED_VARIABLE")
    val context = LocalContext.current
    
    // navigateToProfile 상태가 변경되면 프로필 화면으로 이동
    LaunchedEffect(uiState.navigateToProfile) {
        uiState.navigateToProfile?.let { userId ->
            // 프로필 화면으로 이동 (프로필 화면에서 채팅하기 버튼으로 채팅 화면 이동)
            val person = uiState.nearbyPeople.find { it.userId == userId }
            if (person != null) {
                val route = "profile/${userId}/${person.name}/${person.distance.toInt()}m"
                navController.navigate(route)
            }
            viewModel.onProfileScreenNavigated() // 네비게이션 후 상태 초기화
        }
    }
    
    // 생명주기에 따른 스캔 상태 관리
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // 화면으로 돌아왔을 때 스캔 상태 복원
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.restoreScanningStateIfNeeded()
                    if (uiState.isScanning) {
                        // 스캔 복원 로직
                        rippleVisible.value = true
                        // 스캔 애니메이션 재시작
                        coroutineScope.launch {
                            runRippleAnimation(rippleAnimatable1, rippleAnimatable2, rippleAnimatable3)
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // 화면이 백그라운드로 갈 때 애니메이션만 일시 중단
                    // 실제 스캔 상태는 ViewModel에 유지됨
                    rippleVisible.value = false
                }
                Lifecycle.Event.ON_STOP -> {
                    // 여기서는 애니메이션 관련된 리소스만 정리
                    // 스캔 자체는 ViewModel에서 유지됨
                    rippleVisible.value = false
                }
                else -> { /* 다른 이벤트는 처리하지 않음 */ }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // 버튼 스케일 애니메이션
    val buttonScale by animateFloatAsState(
        targetValue = if (uiState.isScanning) 0.9f else 1f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "buttonScale"
    )
    
    // 버튼 내부 빛남 효과를 위한 애니메이션
    val buttonGlowAnimation = rememberInfiniteTransition(label = "buttonGlow")
    val buttonGlowAlpha by buttonGlowAnimation.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing)
        ),
        label = "buttonGlowAlpha"
    )
    
    // 레이더 회전 애니메이션
    val radarRotation = rememberInfiniteTransition(label = "radarRotation")
    val radarAngle by radarRotation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing)
        ),
        label = "radarAngle"
    )
    
    // 점 깜빡임 애니메이션
    val dotPulseAnimation = rememberInfiniteTransition(label = "dotPulse")
    val dotPulseScale by dotPulseAnimation.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing)
        ),
        label = "dotPulseScale"
    )
    
    // 점 빛남 효과 애니메이션
    val dotGlowAnimation = rememberInfiniteTransition(label = "dotGlow")
    val dotGlowAlpha by dotGlowAnimation.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing)
        ),
        label = "dotGlowAlpha"
    )
    
    // 스캔 상태에 따른 애니메이션 제어
    LaunchedEffect(uiState.isScanning) {
        if (uiState.isScanning) {
            rippleVisible.value = true
            
            // 리플 애니메이션 무한 반복
            while (uiState.isScanning) {
                // 리플 애니메이션 실행
                runRippleAnimation(rippleAnimatable1, rippleAnimatable2, rippleAnimatable3)
                
                // 중요: 애니메이션 재시작을 위한 대기 시간
                delay(1000) // 1000ms 대기로 변경 - 파동 간격 늘림
            }
        } else {
            // 스캔 종료 시 애니메이션 상태 초기화
            rippleVisible.value = false
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // 그라데이션 배경
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colorScheme.background
                )
                .windowInsetsPadding(WindowInsets.safeDrawing) // 시스템 바 영역 패딩 적용
                .padding(paddingValues) // 네비게이션 바 영역 패딩 적용
                .then(modifier),
            contentAlignment = Alignment.Center
        ) {

            // 메인 컨텐츠 (하위 컴포넌트로 추출)
            MainContent(
                isScanning = uiState.isScanning,
                onScanToggle = { 
                    // 권한과 블루투스 상태가 확인되면 스캔 토글
                    viewModel.toggleScan()
                },
                nearbyPeople = uiState.nearbyPeople,
                showPersonListModal = uiState.showPersonListModal,
                onShowListToggle = { viewModel.togglePersonListModal() },
                onPersonClick = { userId ->
                    // 채팅 화면으로 이동
                    navController.navigate("${AppDestinations.DIRECT_CHAT_ROUTE.replace("{userId}", userId)}")
                    viewModel.togglePersonListModal() // 모달 닫기
                },
                onCallClick = { userId ->
                    // 통화 화면으로 이동
                    navController.navigate("${AppDestinations.OUTGOING_CALL_ROUTE.replace("{receiverId}", userId)}")
                    viewModel.togglePersonListModal() // 모달 닫기
                },
                rippleStates = Triple(
                    RippleState(rippleVisible.value, rippleAnimatable1.value),
                    RippleState(rippleVisible.value, rippleAnimatable2.value),
                    RippleState(rippleVisible.value, rippleAnimatable3.value)
                ),
                animationValues = AnimationValues(
                    buttonScale = buttonScale,
                    buttonGlowAlpha = buttonGlowAlpha,
                    radarAngle = radarAngle,
                    dotPulseScale = dotPulseScale,
                    dotGlowAlpha = dotGlowAlpha
                ),
                buttonText = uiState.buttonText,
                subTextVisible = uiState.subTextVisible,
                showListButton = uiState.showListButton
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    LanternsTheme {
        MainScreen(
            navController = rememberNavController()
        )
    }
}

/**
 * 리플 애니메이션을 실행하는 함수
 */
private suspend fun runRippleAnimation(
    rippleAnimatable1: Animatable<Float, *>,
    rippleAnimatable2: Animatable<Float, *>,
    rippleAnimatable3: Animatable<Float, *>
) {
    coroutineScope {
        // 첫 번째 리플 애니메이션 - 더 느리게 확산
        launch {
            rippleAnimatable1.snapTo(0f)
            rippleAnimatable1.animateTo(
                targetValue = 1f,
                animationSpec = tween(1800, easing = LinearEasing) // 지속 시간 증가
            )
        }
        
        // 두 번째 리플 애니메이션 (딜레이 적용) - 더 느리게 확산
        launch {
            delay(350) // 딜레이 약간 증가
            rippleAnimatable2.snapTo(0f)
            rippleAnimatable2.animateTo(
                targetValue = 1f,
                animationSpec = tween(1800, easing = LinearEasing) // 지속 시간 증가
            )
        }
        
        // 세 번째 리플 애니메이션 (딜레이 적용) - 더 느리게 확산
        launch {
            delay(700) // 딜레이 약간 증가
            rippleAnimatable3.snapTo(0f)
            rippleAnimatable3.animateTo(
                targetValue = 1f,
                animationSpec = tween(1800, easing = LinearEasing) // 지속 시간 증가
            )
        }
    }
} 