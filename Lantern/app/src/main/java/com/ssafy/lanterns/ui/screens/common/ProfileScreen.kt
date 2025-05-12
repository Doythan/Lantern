package com.ssafy.lanterns.ui.screens.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ssafy.lanterns.R
import com.ssafy.lanterns.ui.theme.*

data class UserProfileData(
    val userId: String,
    val name: String,
    val distance: String,
    val profileImageResId: Int = R.drawable.default_profile // 기본 프로필 이미지 리소스 ID (resources 폴더에 추가 필요)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    userData: UserProfileData
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("프로필", color = TextWhite) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = TextWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextWhite
                )
            )
        },
        containerColor = NavyTop
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(NavyTop, NavyBottom)
                    )
                )
        ) {
            // 프로필 내용
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(40.dp))
                
                // 프로필 이미지
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFD700)) // 원형 배경색
                ) {
                    Image(
                        painter = painterResource(id = userData.profileImageResId),
                        contentDescription = "프로필 이미지",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 사용자 이름
                Text(
                    text = userData.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 거리 정보
                Text(
                    text = userData.distance,
                    fontSize = 18.sp,
                    color = BleAccent
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // 채팅하기 버튼
                Button(
                    onClick = {
                        // 채팅 화면으로 이동
                        navController.navigate("directchat/${userData.userId}")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BleAccent
                    )
                ) {
                    Text(
                        text = "채팅하기",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp)) // 하단 여백
            }
        }
    }
}

// 테스트를 위한 미리보기
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreenPreview() {
    LanternTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = NavyTop
        ) {
            ProfileScreen(
                navController = androidx.navigation.compose.rememberNavController(),
                userData = UserProfileData(
                    userId = "1",
                    name = "도경원",
                    distance = "35m"
                )
            )
        }
    }
} 