package com.ssafy.lantern.ui.screens.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.lantern.R
import com.ssafy.lantern.ui.theme.LanternTheme
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.Icons

/**
 * 로그인 화면
 */
@Composable
fun LoginScreen(
    onSignUpClick: () -> Unit, 
    onMyPageClick: () -> Unit, 
    onFriendListClick: () -> Unit,
    onIncomingCallClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo
        Image(
            painter = painterResource(id = R.drawable.lantern_image),
            contentDescription = "Lantern Logo",
            modifier = Modifier
                .size(250.dp)
                .padding(bottom = 16.dp)
        )
        
        // App Name
        Text(
            text = "LANTERN",
            color = Color(0xFFFFD700),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Google Login Button
        OutlinedButton(
            onClick = { /* TODO */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                backgroundColor = Color.White,
                contentColor = Color.Black
            ),
            border = BorderStroke(1.dp, Color.LightGray)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 구글 로고 사용
                Image(
                    painter = painterResource(id = R.drawable.google_logo),
                    contentDescription = "Google Logo",
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "Google로 로그인",
                    fontSize = 16.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // My Page Button (임시)
        OutlinedButton(
            onClick = onMyPageClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                backgroundColor = Color.Transparent,
                contentColor = Color(0xFFFFD700)
            ),
            border = BorderStroke(1.dp, Color(0xFFFFD700)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "마이페이지 (테스트)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Friend List Button (임시)
        OutlinedButton(
            onClick = onFriendListClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                backgroundColor = Color.Transparent,
                contentColor = Color(0xFFFFD700)
            ),
            border = BorderStroke(1.dp, Color(0xFFFFD700)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "최근 통화 (테스트)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 전화 수신 테스트 버튼 추가
        OutlinedButton(
            onClick = onIncomingCallClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                backgroundColor = Color.Transparent,
                contentColor = Color(0xFFFFD700)
            ),
            border = BorderStroke(1.dp, Color(0xFFFFD700)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "전화 수신 화면 (테스트)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 회원가입 안내 텍스트
        TextButton(
            onClick = onSignUpClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "아이디가 없으신가요? 회원가입하기",
                color = Color(0xFFFFD700),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    LanternTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            LoginScreen(
                onSignUpClick = {},
                onMyPageClick = {},
                onFriendListClick = {},
                onIncomingCallClick = {}
            )
        }
    }
}