package com.ssafy.lanterns.ui.screens.call

import android.Manifest
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.ssafy.lanterns.ui.theme.*
import com.ssafy.lanterns.ui.util.getProfileImageResId
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import kotlinx.coroutines.delay

/**
 * 전화 거는 중 화면
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OutgoingCallScreen(
    receiverName: String,
    receiverId: Int = 1,
    onCancelClick: () -> Unit,
    viewModel: CallViewModel = hiltViewModel()
) {
    val hasAudioPermission by viewModel.hasAudioPermission.collectAsState()
    
    // 마이크 권한 상태
    val micPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    
    // 연결 대기 상태 표시
    var connectionSeconds by remember { mutableStateOf(0) }
    
    // 연결 대기 시간 업데이트
    LaunchedEffect(Unit) {
        // 권한 요청
        if (!hasAudioPermission) {
            micPermissionState.launchPermissionRequest()
        }
        
        // 연결 시간 카운터
        while (true) {
            delay(1000)
            connectionSeconds++
        }
    }
    
    // 연결 시도 시간 표시 (1분 이상 지속되면 분:초 형식으로 표시)
    val connectionTimeText = remember(connectionSeconds) {
        if (connectionSeconds < 60) {
            "연결 중... ${connectionSeconds}초"
        } else {
            val minutes = connectionSeconds / 60
            val seconds = connectionSeconds % 60
            "연결 중... ${minutes}분 ${seconds}초"
        }
    }
    
    // 전화 걸고 있음 애니메이션을 위한 알파값
    val infiniteTransition = rememberInfiniteTransition(label = "calling_animation")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000)
        ),
        label = "alpha_animation"
    )
    
    // 시스템 바 패딩 계산
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = statusBarPadding.calculateTopPadding(),
                    bottom = navigationBarPadding.calculateBottomPadding()
                )
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 상단 정보
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = receiverName,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = connectionTimeText,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    fontSize = 18.sp
                )
                
                // 마이크 권한 알림
                if (!hasAudioPermission && !micPermissionState.status.isGranted) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "마이크 권한이 필요합니다",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 16.sp
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { 
                            micPermissionState.launchPermissionRequest()
                        }
                    ) {
                        Text("권한 요청")
                    }
                }
            }
            
            // 중앙 프로필
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(DarkCardBackground),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = getProfileImageResId(receiverId)),
                    contentDescription = "Receiver Profile",
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            
            // 하단 통화 종료 버튼
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Button(
                    onClick = onCancelClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Error
                    ),
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CallEnd,
                        contentDescription = "End Call",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "통화 취소",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OutgoingCallScreenPreview() {
    LanternsTheme {
        OutgoingCallScreen(
            receiverName = "김민수",
            receiverId = 2,
            onCancelClick = {}
        )
    }
} 