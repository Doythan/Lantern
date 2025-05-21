package com.ssafy.lanterns.ui.screens.call

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.lanterns.ui.theme.*
import com.ssafy.lanterns.ui.util.getProfileImageResId
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import android.util.Log

@Composable
fun OutgoingCallScreen(
    receiverName: String,
    receiverId: Int = 1,
    onCancelClick: () -> Unit,
    // 여기가 중요한 변경점: 기본값 제거
    callViewModel: CallViewModel
) {
    val uiState by callViewModel.uiState.collectAsState()

    // 디버깅용 로그 추가
    DisposableEffect(Unit) {
        Log.d("OutgoingCallScreen", "화면 시작 - 사용 중인 callViewModel: $callViewModel")
        onDispose {
            Log.d("OutgoingCallScreen", "화면 종료")
        }
    }

    // callState 변경 감지용 LaunchedEffect 추가
    LaunchedEffect(uiState.callState) {
        Log.d("OutgoingCallScreen", "상태 변경 감지: ${uiState.callState}")
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
            // 상단 여백
            Spacer(modifier = Modifier.height(32.dp))

            // 프로필 이미지
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
                        .clip(CircleShape)
                )
            }

            // 수신자 정보 영역
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 수신자 이름
                Text(
                    text = receiverName,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // "통화 거는 중..." 텍스트 (깜빡이는 효과)
                Text(
                    text = "통화 거는 중...",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    fontSize = 18.sp,
                    modifier = Modifier.alpha(alpha)
                )
            }

            // 하단 버튼 영역 (하단 내비게이션 바 고려)
            Box(
                modifier = Modifier
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
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
                        contentDescription = "Cancel Call",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        // 오류 메시지 표시
        if (uiState.errorMessage != null) {
            AlertDialog(
                onDismissRequest = { callViewModel.clearErrorMessage() },
                title = { Text("통화 오류") },
                text = { Text(uiState.errorMessage!!) },
                confirmButton = {
                    Button(onClick = { callViewModel.clearErrorMessage() }) {
                        Text("확인")
                    }
                }
            )
        }
    }
}