package com.ssafy.lanterns.ui.screens.call

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ssafy.lanterns.ui.theme.*
import com.ssafy.lanterns.ui.util.getProfileImageResId
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars

// 통화 중 화면
@Composable
fun OngoingCallScreen(
    callerName: String,
    callerId: Int = 1,
    onEndCallClick: () -> Unit,
    viewModel: CallViewModel = hiltViewModel()
) {
    // 음소거, 스피커 상태
    val isMuted by viewModel.isMuted.collectAsState()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    // 현재 상태가 OngoingCall인 경우에만 통화 시간 표시
    val callDurationSeconds = if (uiState is CallUiState.OngoingCall) {
        (uiState as CallUiState.OngoingCall).callDurationSeconds
    } else {
        0
    }
    
    // 통화 시간 업데이트
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            // 통화 시간은 서비스에서 관리되므로 여기서는 UI 갱신만 진행
        }
    }
    
    // 통화 시간 포맷팅
    val formattedDuration = remember(callDurationSeconds) {
        val minutes = callDurationSeconds / 60
        val seconds = callDurationSeconds % 60
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
                
                // 턴 상태 표시
                if (uiState is CallUiState.OngoingCall) {
                    val ongoingState = uiState as CallUiState.OngoingCall
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = when {
                            ongoingState.isMyTurn -> "내 차례 (말하는 중)"
                            ongoingState.opponentIsSpeaking -> "상대방이 말하는 중"
                            ongoingState.waitingForTurn -> "발언권 요청 중..."
                            else -> "대기 중"
                        },
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp
                    )
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
                    painter = painterResource(id = getProfileImageResId(callerId)),
                    contentDescription = "Caller Profile",
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                )
            }
            
            // 하단 통화 컨트롤
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
                                    if (isSpeakerOn) activeButtonColor else inactiveButtonColor,
                                )
                                .clickable { 
                                    viewModel.toggleSpeaker()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VolumeUp,
                                contentDescription = "Speaker",
                                tint = if (isSpeakerOn) activeIconColor else inactiveIconColor,
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
                                    if (isMuted) activeButtonColor else inactiveButtonColor,
                                )
                                .clickable { 
                                    viewModel.toggleMute()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MicOff,
                                contentDescription = "Mute",
                                tint = if (isMuted) activeIconColor else inactiveIconColor,
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
                
                // 발언 요청 버튼 (턴 관리)
                if (uiState is CallUiState.OngoingCall) {
                    val ongoingState = uiState as CallUiState.OngoingCall
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { 
                            if (ongoingState.isMyTurn) {
                                viewModel.endVoiceTurn()
                            } else {
                                viewModel.requestVoiceTurn()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (ongoingState.isMyTurn) 
                                MaterialTheme.colorScheme.error 
                            else 
                                MaterialTheme.colorScheme.primary
                        ),
                        enabled = !ongoingState.waitingForTurn
                    ) {
                        Text(
                            text = if (ongoingState.isMyTurn) "발언 종료" else "발언 요청",
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
    }
}

@Preview(showBackground = true)
@Composable
fun OngoingCallScreenPreview() {
    LanternsTheme {
        OngoingCallScreen(
            callerName = "김민수",
            callerId = 2,
            onEndCallClick = {}
        )
    }
}