package com.ssafy.lantern.ui.screens.mypage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ssafy.lantern.R
import com.ssafy.lantern.ui.theme.LanternTheme

@Composable
fun MyPageScreen(onBackClick: () -> Unit) {
    var isEditing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("도경원") }
    var email by remember { mutableStateOf("won03289@gmail.com") }
    var nickname by remember { mutableStateOf("@x0248x") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back Button
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onBackClick() }
            )
            
            // Title
            Text(
                text = "프로필 수정",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            // Edit Button
            Text(
                text = if (isEditing) "완료" else "수정",
                color = Color(0xFFFFD700),
                fontSize = 16.sp,
                modifier = Modifier.clickable { isEditing = !isEditing }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Profile Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp),
            backgroundColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Image
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray)
                        .clickable(enabled = isEditing) {
                            // Image selection would go here
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.lantern_image),
                        contentDescription = "Profile Image",
                        modifier = Modifier.size(150.dp)
                    )
                }
                
                // 프로필 사진 수정 아이콘 - 항상 존재하되 수정 모드일 때만 보이게 함
                Box(
                    modifier = Modifier
                        .offset(x = 70.dp, y = (-40).dp)  // 프로필 사진 오른쪽 아래에 겹치도록 위치 조정
                        .size(50.dp)  // 크기 약간 줄임
                        .alpha(if (isEditing) 1f else 0f)  // 수정 모드가 아닐 때는 전체를 투명하게
                        .background(Color(0xFFFFD700), CircleShape)
                        .padding(10.dp)
                        .clickable(enabled = isEditing) { /* 이미지 선택 로직 */ }
                        .zIndex(10f),  // zIndex 값을 높게 유지
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile Image",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)  // 아이콘 크기 줄임
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                // Name Field
                SimpleProfileField(
                    label = "이름",
                    value = name,
                    onValueChange = { name = it },
                    isEditing = isEditing
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Nickname Field
                SimpleProfileField(
                    label = "사용자 이름",
                    value = nickname,
                    onValueChange = { nickname = it },
                    isEditing = isEditing
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Email Field
                SimpleProfileField(
                    label = "이메일",
                    value = email,
                    onValueChange = { email = it },
                    isEditing = isEditing
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Log Out Button
                OutlinedButton(
                    onClick = { onBackClick() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        backgroundColor = Color.Transparent,
                        contentColor = Color(0xFFFFD700)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFFFD700))
                ) {
                    Text(
                        text = "로그아웃",
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SimpleProfileField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isEditing: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 레이블
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Gray
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 중요: 편집 모드와 상관없이 항상 두 컴포넌트를 모두 렌더링하고
        // alpha 값으로 보이고 안 보이게 처리
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        ) {
            // 일반 텍스트 - 항상 동일한 위치에 렌더링
            Text(
                text = value,
                fontSize = 16.sp,
                color = Color.Black,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.alpha(if (isEditing) 0f else 1f)
            )
            
            // 편집 필드 - 항상 동일한 위치에 렌더링하되 편집 모드가 아닐 때는 투명하게
            if (isEditing) {
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
        
        // 구분선
        Divider(
            color = if (isEditing) Color(0xFFFFD700) else Color.LightGray,
            thickness = 1.dp
        )
        
        // 필드 사이 간격
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun MyPageScreenPreview() {
    LanternTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            MyPageScreen(onBackClick = {})
        }
    }
}