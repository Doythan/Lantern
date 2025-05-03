package com.ssafy.lantern.ui.screens.call

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.lantern.R
import com.ssafy.lantern.ui.theme.LanternTheme
import kotlinx.coroutines.delay

// 통화 중 화면
@Composable
fun OngoingCallScreen(
    callerName: String,
    onEndCallClick: () -> Unit
) {
    var callDuration by remember { mutableStateOf(0) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    
    // 통화 시간 업데이트
    LaunchedEffect(Unit) {
        while(true) {
            delay(1000)
            callDuration++
        }
    }
    
    // 통화 시간 포맷팅
    val formattedDuration = remember(callDuration) {
        val minutes = callDuration / 60
        val seconds = callDuration % 60
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 상단 정보
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 32.dp)
            ) {
                Text(
                    text = callerName,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = formattedDuration,
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
            
            // 중앙 프로필
            Image(
                painter = painterResource(id = R.drawable.lantern_image),
                contentDescription = "Caller Profile",
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFD700))
            )
            
            // 하단 통화 컨트롤
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                // 통화 기능 버튼들
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 스피커 버튼
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    if (isSpeakerOn) Color(0xFFFFD700) else Color.DarkGray,
                                    CircleShape
                                )
                                .clickable { 
                                    isSpeakerOn = !isSpeakerOn
                                    // 스피커를 켜면 음소거는 자동으로 해제
                                    if (isSpeakerOn) {
                                        isMuted = false
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = android.R.drawable.ic_lock_silent_mode_off),
                                contentDescription = "Speaker",
                                tint = if (isSpeakerOn) Color.Black else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "스피커",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                    
                    // 음소거 버튼
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    if (isMuted) Color(0xFFFFD700) else Color.DarkGray,
                                    CircleShape
                                )
                                .clickable { 
                                    isMuted = !isMuted
                                    // 음소거를 켜면 스피커는 자동으로 해제
                                    if (isMuted) {
                                        isSpeakerOn = false
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = android.R.drawable.ic_lock_silent_mode),
                                contentDescription = "Mute",
                                tint = if (isMuted) Color.Black else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "음소거",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // 통화 종료 버튼
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.Red, CircleShape)
                        .clickable(onClick = onEndCallClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_call),
                        contentDescription = "End Call",
                        tint = Color.White,
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
    LanternTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            OngoingCallScreen(
                callerName = "도경원",
                onEndCallClick = {}
            )
        }
    }
}