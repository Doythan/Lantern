package com.ssafy.lantern.ui.screens.call

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.lantern.R
import com.ssafy.lantern.ui.theme.LanternTheme

/**
 * 전화 수신 화면
 */
@Composable
fun IncomingCallScreen(
    callerName: String,
    onRejectClick: () -> Unit,
    onAcceptClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(64.dp))
        
        // 발신자 프로필 이미지
        Image(
            painter = painterResource(id = R.drawable.lantern_image),
            contentDescription = "Caller Profile",
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFD700))
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 발신자 이름
        Text(
            text = callerName,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        
        
        Spacer(modifier = Modifier.weight(1f))
        
        // 통화 버튼 영역
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 거절 버튼
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onRejectClick,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color.Red
                    ),
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Image(
                        painter = painterResource(id = android.R.drawable.ic_menu_call),
                        contentDescription = "Reject Call",
                        modifier = Modifier
                            .size(65.dp)
                            .padding(4.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "거절",
                    color = Color.White,
                    fontSize = 20.sp
                )
            }
            
            // 수락 버튼
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onAcceptClick,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color.Green
                    ),
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Image(
                        painter = painterResource(id = android.R.drawable.ic_menu_call),
                        contentDescription = "Accept Call",
                        modifier = Modifier
                            .size(65.dp)
                            .padding(4.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "수락",
                    color = Color.White,
                    fontSize = 20.sp
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun IncomingCallScreenPreview() {
    LanternTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            IncomingCallScreen(
                callerName = "도경원",
                onRejectClick = {},
                onAcceptClick = {}
            )
        }
    }
}