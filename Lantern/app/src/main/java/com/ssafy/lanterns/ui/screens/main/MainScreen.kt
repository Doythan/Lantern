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
import com.ssafy.lanterns.ui.theme.LanternTheme
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
    val aiActive by viewModel.aiActive.collectAsState()
    
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
    
    // =====================================================
    // TODO: [BLE] BLE 권한 및 블루투스 상태 확인 로직 구현 필요
    // 앱 시작 또는 화면 활성화 시 권한 확인 및 ViewModel에 상태 업데이트 (viewModel.updateBlePermissionStatus, viewModel.updateBluetoothState 호출)
    // LaunchedEffect(Unit) {
    //     // 블루투스 권한 확인
    //     val hasBluetoothPermissions = checkBluetoothPermissions(context)
    //     viewModel.updateBlePermissionStatus(hasBluetoothPermissions)
    //     
    //     // 블루투스 활성화 상태 확인
    //     val isBluetoothEnabled = isBluetoothEnabled(context)
    //     viewModel.updateBluetoothState(isBluetoothEnabled)
    // }
    // =====================================================
    
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
                        
                        // =====================================================
                        // TODO: BLE 서비스 상태 확인 및 복원
                        // 앱이 다시 포그라운드로 돌아올 때 BLE 서비스 상태를 확인하고 복원합니다.
                        // 
                        // val isBluetoothEnabled = isBluetoothEnabled(context)
                        // viewModel.updateBluetoothState(isBluetoothEnabled)
                        // 
                        // if (uiState.isBleServiceActive) {
                        //     // BLE 서비스가 활성화 상태라면 상태를 확인하고 필요시 다시 시작
                        //     checkAndRestartBleServiceIfNeeded(context, viewModel)
                        // }
                        // =====================================================
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
                delay(800) // 800ms 대기 - 이 값을 더 크게 하면 파동이 느려짐
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
            // AI 호출 버튼
            FloatingActionButton(
                onClick = { 
                    // 라우트 이동 대신 AI 활성화 함수 호출
                    viewModel.activateAI()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice AI"
                )
            }
            
            // 메인 컨텐츠 (하위 컴포넌트로 추출)
            MainContent(
                isScanning = uiState.isScanning,
                onScanToggle = { 
                    // =====================================================
                    // TODO: 시작/중지 버튼 클릭 시 BLE 권한 확인 로직
                    // 버튼을 클릭할 때 BLE 권한을 확인하고 필요하다면 권한 요청을 수행합니다.
                    // 권한이 있으면 스캔 토글 함수를 호출합니다.
                    // 
                    // if (!uiState.isScanning) {
                    //     // 시작하기 버튼을 누를 때, BLE 권한 확인
                    //     if (!hasRequiredBlePermissions(context)) {
                    //         // 권한이 없으면 권한 요청 다이얼로그 표시
                    //         requestBlePermissions(context)
                    //         return@MainContent
                    //     }
                    //     
                    //     // 블루투스가 활성화되었는지 확인
                    //     if (!isBluetoothEnabled(context)) {
                    //         // 블루투스가 비활성화되어 있으면 활성화 요청 다이얼로그 표시
                    //         requestEnableBluetooth(context)
                    //         return@MainContent
                    //     }
                    // }
                    // =====================================================
                    
                    // 권한과 블루투스 상태가 확인되면 스캔 토글
                    viewModel.toggleScan()
                },
                nearbyPeople = uiState.nearbyPeople,
                showPersonListModal = uiState.showPersonListModal,
                onShowListToggle = { viewModel.togglePersonListModal() },
                onPersonClick = viewModel::onPersonClick,
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
                statusText = uiState.statusText,
                subTextVisible = uiState.subTextVisible,
                showListButton = uiState.showListButton
            )
            
            // AI 대화상자 표시 - OnDeviceAIScreen 대신 OnDeviceAIDialog 사용
            if (aiActive) {
                OnDeviceAIDialog(
                    onDismiss = { viewModel.deactivateAI() }
                )
            }
        }
    }
}

/**
 * BLE 권한 확인 함수
 * BLE 기능을 사용하기 위해 필요한 권한이 있는지 확인합니다.
 */
// =====================================================
// TODO: BLE 권한 확인 함수 구현
// 
// private fun hasRequiredBlePermissions(context: Context): Boolean {
//     // Android 12 이상에서는 BLUETOOTH_SCAN, BLUETOOTH_CONNECT 권한 확인
//     // Android 11 이하에서는 ACCESS_FINE_LOCATION 권한 확인
//     
//     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//         return ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == 
//                 PackageManager.PERMISSION_GRANTED &&
//                ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == 
//                 PackageManager.PERMISSION_GRANTED
//     } else {
//         return ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == 
//                 PackageManager.PERMISSION_GRANTED
//     }
// }
// =====================================================

/**
 * BLE 권한 요청 함수
 * BLE 기능을 사용하기 위해 필요한 권한을 요청합니다.
 */
// =====================================================
// TODO: BLE 권한 요청 함수 구현
// 
// private fun requestBlePermissions(context: Context) {
//     // ActivityResultLauncher를 통해 권한 요청
//     // 메인 화면 Composable에서는 rememberLauncherForActivityResult를 사용해야 함
//     // 이 함수는 예시일 뿐, 실제로는 MainScreen Composable 내부에서 구현해야 함
//     
//     // val launcher = rememberLauncherForActivityResult(
//     //     contract = ActivityResultContracts.RequestMultiplePermissions(),
//     //     onResult = { permissions ->
//     //         val allGranted = permissions.values.all { it }
//     //         viewModel.updateBlePermissionStatus(allGranted)
//     //     }
//     // )
//     
//     // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//     //     launcher.launch(
//     //         arrayOf(
//     //             android.Manifest.permission.BLUETOOTH_SCAN,
//     //             android.Manifest.permission.BLUETOOTH_CONNECT
//     //         )
//     //     )
//     // } else {
//     //     launcher.launch(
//     //         arrayOf(
//     //             android.Manifest.permission.ACCESS_FINE_LOCATION
//     //         )
//     //     )
//     // }
// }
// =====================================================

/**
 * 블루투스 활성화 상태 확인 함수
 * 블루투스가 활성화되어 있는지 확인합니다.
 */
// =====================================================
// TODO: 블루투스 활성화 상태 확인 함수 구현
// 
// private fun isBluetoothEnabled(context: Context): Boolean {
//     val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
//     val bluetoothAdapter = bluetoothManager.adapter
//     return bluetoothAdapter?.isEnabled == true
// }
// =====================================================

/**
 * 블루투스 활성화 요청 함수
 * 블루투스가 비활성화되어 있는 경우 활성화를 요청합니다.
 */
// =====================================================
// TODO: 블루투스 활성화 요청 함수 구현
// 
// private fun requestEnableBluetooth(context: Context) {
//     // ActivityResultLauncher를 통해 블루투스 활성화 요청
//     // 메인 화면 Composable에서는 rememberLauncherForActivityResult를 사용해야 함
//     // 이 함수는 예시일 뿐, 실제로는 MainScreen Composable 내부에서 구현해야 함
//     
//     // val launcher = rememberLauncherForActivityResult(
//     //     contract = ActivityResultContracts.StartActivityForResult(),
//     //     onResult = { result ->
//     //         val isEnabled = result.resultCode == Activity.RESULT_OK
//     //         viewModel.updateBluetoothState(isEnabled)
//     //     }
//     // )
//     
//     // val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//     // launcher.launch(enableBtIntent)
// }
// =====================================================

/**
 * BLE 서비스 상태 확인 및 재시작 함수
 * BLE 서비스가 중단되었다면 다시 시작합니다.
 */
// =====================================================
// TODO: BLE 서비스 상태 확인 및 재시작 함수 구현
// 
// private fun checkAndRestartBleServiceIfNeeded(context: Context, viewModel: MainViewModel) {
//     // BleServiceManager를 통해 서비스 상태를 확인하고 필요하다면 재시작
//     // 이 함수는 화면이 다시 활성화될 때 호출됩니다.
//     
//     // val bleServiceManager = BleServiceManager.getInstance(context)
//     // if (!bleServiceManager.isServiceRunning() && viewModel.uiState.value.isBleServiceActive) {
//     //     bleServiceManager.startBleService()
//     // }
// }
// =====================================================

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    LanternTheme {
        Surface {
            MainScreen(navController = rememberNavController())
        }
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
        // 첫 번째 리플 애니메이션
        launch {
            rippleAnimatable1.snapTo(0f)
            rippleAnimatable1.animateTo(
                targetValue = 1f,
                animationSpec = tween(1500, easing = LinearEasing)
            )
        }
        
        // 두 번째 리플 애니메이션 (딜레이 적용)
        launch {
            delay(300) // 300ms 지연
            rippleAnimatable2.snapTo(0f)
            rippleAnimatable2.animateTo(
                targetValue = 1f,
                animationSpec = tween(1500, easing = LinearEasing)
            )
        }
        
        // 세 번째 리플 애니메이션 (딜레이 적용)
        launch {
            delay(600) // 600ms 지연
            rippleAnimatable3.snapTo(0f)
            rippleAnimatable3.animateTo(
                targetValue = 1f,
                animationSpec = tween(1500, easing = LinearEasing)
            )
        }
    }
} 