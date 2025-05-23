package com.ssafy.lanterns.ui.screens.call

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.ssafy.lanterns.ui.navigation.AppDestinations
import kotlinx.coroutines.delay

/**
 * 통화 화면 - 상태에 따라 적절한 화면 표시
 * 
 * @param navController 내비게이션 컨트롤러
 * @param deviceAddress 통화 요청 시 상대방 디바이스 주소 (없으면 수신 대기 상태)
 * @param deviceName 상대방 이름
 * @param profileId 상대방 프로필 이미지 ID
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CallScreen(
    navController: NavController,
    deviceAddress: String? = null,
    deviceName: String? = null,
    profileId: Int = 1,
    viewModel: CallViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val hasAudioPermission by viewModel.hasAudioPermission.collectAsState()
    
    // 마이크 권한 확인
    val micPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    
    // 권한 요청 처리
    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            micPermissionState.launchPermissionRequest()
        }
    }
    
    // 라이프사이클 이벤트 관찰
    val lifecycleOwner = LocalLifecycleOwner.current
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_CREATE) {
                viewModel.initialize()
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.endCall() // 화면을 나갈 때 통화 종료
        }
    }
    
    // 디바이스 주소가 전달되면 통화 시작
    LaunchedEffect(deviceAddress) {
        if (!deviceAddress.isNullOrEmpty()) {
            // 약간의 지연 후 통화 시작 (UI가 준비되도록)
            delay(500)
            viewModel.initiateCall(deviceAddress, deviceName)
        }
    }
    
    when (val state = uiState) {
        is CallUiState.Idle -> {
            // 통화가 종료되었거나 초기 상태인 경우 이전 화면으로 돌아감
            LaunchedEffect(state) {
                if (deviceAddress != null) {
                    navController.popBackStack()
                }
            }
        }
        
        is CallUiState.OutgoingCall -> {
            OutgoingCallScreen(
                receiverName = deviceName ?: "알 수 없음",
                receiverId = profileId,
                onCancelClick = { viewModel.endCall() }
            )
        }
        
        is CallUiState.IncomingCall -> {
            IncomingCallScreen(
                callerName = state.deviceName,
                callerId = profileId,
                onRejectClick = { viewModel.rejectCall() },
                onAcceptClick = { viewModel.acceptCall() }
            )
        }
        
        is CallUiState.OngoingCall -> {
            OngoingCallScreen(
                callerName = state.deviceName,
                callerId = profileId,
                onEndCallClick = { viewModel.endCall() }
            )
        }
        
        is CallUiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
                
                // 2초 후 돌아가기
                LaunchedEffect(state) {
                    delay(2000)
                    navController.popBackStack()
                }
            }
        }
        
        else -> {
            // 예상치 못한 상태에 대한 처리
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
} 