package com.ssafy.lanterns.ui.screens.call

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun OngoingCallScreen(
    callerName: String,
    callerId: Int = 1,
    onEndCallClick: () -> Unit,
    // 여기가 중요한 변경점: 기본값 제거
    callViewModel: CallViewModel
) {
    val uiState by callViewModel.uiState.collectAsState()

    // 디버깅용 로그 추가
    DisposableEffect(Unit) {
        Log.d("OngoingCallScreen", "화면 시작 - 사용 중인 callViewModel: $callViewModel")
        callViewModel.onScreenActive()
        callViewModel.setKeepConnectionAlive(true)

        onDispose {
            Log.d("OngoingCallScreen", "화면 종료 - 연결 유지 모드 강제 설정")
            // 화면이 사라져도 연결은 유지
            callViewModel.setKeepConnectionAlive(true)
            // 추가: 화면이 종료될 때도 활성 상태 유지
            callViewModel.onScreenActive()
        }
    }

    // callState 변경 감지용 LaunchedEffect 추가
    LaunchedEffect(uiState.callState) {
        Log.d("OngoingCallScreen", "상태 변경 감지: ${uiState.callState}")
    }

    // 통화 시간 포맷팅
    val formattedDuration = remember(uiState.callDuration) {
        val minutes = uiState.callDuration / 60
        val seconds = uiState.callDuration % 60
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }

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
                    text = callerName,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = formattedDuration,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    fontSize = 18.sp
                )
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
                    painter = painterResource(id = getProfileImageResId(callerId)),
                    contentDescription = "Caller Profile",
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                )
            }

            // 하단 통화 컨트롤 (네비게이션 바 고려)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                // 통화 기능 버튼들
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val inactiveButtonColor = MaterialTheme.colorScheme.surfaceVariant
                    val activeButtonColor = MaterialTheme.colorScheme.primary
                    val inactiveIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    val activeIconColor = MaterialTheme.colorScheme.onBackground

                    // 스피커 버튼
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.isSpeakerOn) activeButtonColor else inactiveButtonColor,
                                )
                                .clickable { callViewModel.toggleSpeaker() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (uiState.isSpeakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                                contentDescription = "Speaker",
                                tint = if (uiState.isSpeakerOn) activeIconColor else inactiveIconColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "스피커",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // 음소거 버튼
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.isMuted) activeButtonColor else inactiveButtonColor,
                                )
                                .clickable { callViewModel.toggleMute() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (uiState.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                contentDescription = "Mute",
                                tint = if (uiState.isMuted) activeIconColor else inactiveIconColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "음소거",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 통화 종료 버튼
                Button(
                    onClick = onEndCallClick,
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