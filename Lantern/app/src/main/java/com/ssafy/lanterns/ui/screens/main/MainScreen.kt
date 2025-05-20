package com.ssafy.lanterns.ui.screens.main

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
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
import android.util.Log
import com.ssafy.lanterns.ui.navigation.AppDestinations
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.ssafy.lanterns.ui.screens.main.components.RescueAlertNotification

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
    viewModel: MainViewModel = hiltViewModel(),
    onDeviceAIViewModel: OnDeviceAIViewModel = hiltViewModel()
) {
    // ViewModel로부터 UI 상태 수집
    val uiState by viewModel.uiState.collectAsState()
    val aiActive by viewModel.aiActive.collectAsState()
    
    // 코루틴 스코프
    val coroutineScope = rememberCoroutineScope()
    
    // 리플 애니메이션 관련 상태
    val rippleVisible = remember { mutableStateOf(false) }
    val rippleAnimatable1 = remember { Animatable(0f) }
    val rippleAnimatable2 = remember { Animatable(0f) }
    val rippleAnimatable3 = remember { Animatable(0f) }
    
    // 생명주기 관찰자
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    
    // 필요한 BLE 권한 정의
    val blePermissions = listOfNotNull(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        // Android 12 미만에서는 ACCESS_FINE_LOCATION도 필요할 수 있음
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) Manifest.permission.ACCESS_FINE_LOCATION else null
    ).toTypedArray()

    // 권한 요청 런처
    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsResult ->
        val allGranted = permissionsResult.all { it.value }
        viewModel.updateBlePermissionStatus(allGranted)
        if (!allGranted) {
            Log.e("MainScreen", "필수 BLE 권한이 거부되었습니다.")
            // 사용자에게 권한이 필요함을 알리는 UI 로직 추가 (예: Toast 메시지)
        }
    }
    
    // 블루투스 활성화 요청 런처
    val requestBluetoothEnableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.updateBluetoothState(true)
        } else {
            Log.e("MainScreen", "사용자가 블루투스 활성화를 거부했습니다.")
            // 블루투스가 필요함을 알리는 UI 로직 추가 (예: Toast 메시지)
        }
    }

    // 권한 상태 확인 및 요청 로직
    fun checkAndRequestPermissions() {
        val allPermissionsGranted = blePermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allPermissionsGranted) {
            viewModel.updateBlePermissionStatus(true)
        } else {
            viewModel.updateBlePermissionStatus(false) // 먼저 false로 설정
            requestPermissionsLauncher.launch(blePermissions)
        }
    }
    
    // 블루투스 활성화 요청 함수
    fun requestBluetoothEnable() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (bluetoothManager?.adapter?.isEnabled == false) {
            try {
                val enableBtIntent = android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
                requestBluetoothEnableLauncher.launch(enableBtIntent)
            } catch (e: Exception) {
                Log.e("MainScreen", "블루투스 활성화 요청 중 오류: ${e.message}")
            }
        }
    }

    // 초기화 시 권한 확인 및 요청
    LaunchedEffect(Unit) {
        checkAndRequestPermissions() // 권한 먼저 확인 및 요청
        requestBluetoothEnable() // 블루투스 활성화 요청
        viewModel.initialize(context as Activity)
        // 화면 진입 시 자동으로 스캔 시작
        delay(500) // 초기화 후 약간의 딜레이를 주어 BLE 초기화가 완료되도록 함
        viewModel.startScanAutomatically() // 자동 스캔 시작 메서드 호출
    }
    
    // navigateToProfileServerUserIdString 상태가 변경되면 프로필 화면으로 이동
    LaunchedEffect(uiState.navigateToProfileServerUserIdString) {
        uiState.navigateToProfileServerUserIdString?.let { userId ->
            // 프로필 화면으로 이동
            val person = uiState.nearbyPeople.find { it.serverUserIdString == userId }
            if (person != null) {
                val route = "profile/${userId}/${person.nickname}/${person.signalLevel}"
                navController.navigate(route)
            }
            viewModel.onProfileScreenNavigated() // 네비게이션 후 상태 초기화
        }
    }
    
    // AI 활성화 상태 관찰
    LaunchedEffect(aiActive) {
        if (aiActive) {
            // AI 활성화 로직 (별도 처리)
        }
    }
    
    // 스캔 버튼 클릭 처리 함수 추가
    fun onScanButtonClick() {
        // 현재 상태 확인
        if (!uiState.isBleReady) {
            // BLE가 준비되지 않은 경우 원인 파악 및 조치
            when {
                !uiState.blePermissionsGranted -> {
                    // 권한이 없는 경우
                    Log.d("MainScreen", "권한이 없어 요청합니다.")
                    checkAndRequestPermissions()
                }
                !uiState.isBluetoothEnabled -> {
                    // 블루투스가 꺼져 있는 경우
                    Log.d("MainScreen", "블루투스가 꺼져 있어 활성화를 요청합니다.")
                    requestBluetoothEnable()
                }
                else -> {
                    // 기타 원인 (로그인 등)
                    Log.d("MainScreen", "BLE 준비 안됨. 원인: ${uiState.errorMessage}")
                }
            }
        }
        
        // 항상 toggleScan을 호출하여 ViewModel에서 상태 처리
        viewModel.toggleScan()
    }
    
    // 생명주기에 따른 스캔 상태 관리 및 권한 재확인
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    Log.d("MainScreen", "ON_RESUME: 권한 재확인 및 스캔 상태 복원 시도")
                    checkAndRequestPermissions() // 화면 재개 시 권한 재확인
                    viewModel.onScreenResumed()
                    if (uiState.isScanningActive) {
                        rippleVisible.value = true
                        coroutineScope.launch {
                            runRippleAnimation(rippleAnimatable1, rippleAnimatable2, rippleAnimatable3)
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    Log.d("MainScreen", "ON_PAUSE: 스캔 중지")
                    viewModel.onScreenPaused()
                    rippleVisible.value = false
                }
                else -> { /* 다른 이벤트는 처리하지 않음 */ }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            Log.d("MainScreen", "ON_DISPOSE: Observer 제거")
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // 버튼 스케일 애니메이션
    val buttonScale by animateFloatAsState(
        targetValue = if (uiState.isScanningActive) 0.9f else 1f,
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
    LaunchedEffect(uiState.isScanningActive) {
        if (uiState.isScanningActive) {
            rippleVisible.value = true
            
            // 리플 애니메이션 무한 반복
            while (uiState.isScanningActive) {
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
                    brush = Brush.verticalGradient(
                        colors = listOf(NavyTop, NavyBottom)
                    )
                )
                .windowInsetsPadding(WindowInsets.safeDrawing) // 시스템 바 영역 패딩 적용
                .padding(paddingValues) // 네비게이션 바 영역 패딩 적용
                .then(modifier),
            contentAlignment = Alignment.Center
        ) {
            // 메인 컨텐츠 (하위 컴포넌트로 추출)
            MainContent(
                isScanning = uiState.isScanningActive,
                nearbyPeopleToDisplay = uiState.nearbyPeople.also { 
                   // Log.d("MainScreen", "전체 주변 사람 수: ${it.size}, 필터 적용될 수준: ${uiState.displayDepthLevel}, 표시될 것으로 예상: ${it.filter { p -> p.calculatedVisualDepth <= uiState.displayDepthLevel }.size}")
                }.filter { it.calculatedVisualDepth <= uiState.displayDepthLevel.coerceAtLeast(3) },
                currentSelfDepth = uiState.currentSelfAdvertisedDepth,
                displayDepthLevel = uiState.displayDepthLevel.coerceAtLeast(3),
                onDisplayDepthChange = viewModel::setDisplayDepthLevel,
                showPersonListModal = uiState.showPersonListModal,
                onShowListToggle = viewModel::togglePersonListModal,
                onDismissModal = { viewModel.togglePersonListModal() },
                onPersonClick = viewModel::onPersonClick,
                onCallClick = { serverUserIdString ->
                    navController.navigate("${AppDestinations.OUTGOING_CALL_ROUTE.replace("{receiverId}", serverUserIdString)}")
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
                showListButton = uiState.showListButton,
                onCheckBluetoothState = { onScanButtonClick() }
            )
            AnimatedVisibility(
                visible = uiState.rescueRequestReceived,
                enter = fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(animationSpec = tween(300)),
                modifier = Modifier.align(Alignment.TopCenter) // Box 내에서 TopCenter로 정렬
            ) {
                val message = uiState.rescueRequesterNickname?.let { nickname ->
                    "주변 50m 내에 (${nickname}님)이 도움을 요청하고 있습니다."
                } ?: "주변 50m 내에 구조요청을 원하는 사람이 있습니다."

                RescueAlertNotification(
                    message = message,
                    onDismiss = { viewModel.dismissRescueAlert() }
                )
            }
            
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