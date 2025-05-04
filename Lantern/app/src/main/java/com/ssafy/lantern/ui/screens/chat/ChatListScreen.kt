package com.ssafy.lantern.ui.screens.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.ssafy.lantern.ui.theme.LanternTheme

import com.ssafy.lantern.R


// 더미 데이터 모델
data class ChatItem(
    val id: Int,
    val name: String,
    val lastMessage: String,
    val time: String,
    val unread: Boolean = false
)

@Composable
fun ChatListScreen() {
    val chatList = remember {
        listOf(
            ChatItem(1, "내가진짜도경원", "와, 와이파이 없이 대화 신기하당 ㅎㅎ", "11:20 am", true),
            ChatItem(2, "귀요미", "난 귀요미", "10:20 am"),
            ChatItem(3, "백성욱", "메시지 입력해봐..", "어제"),
            ChatItem(4, "박수민", "나만의 채로서 일타강사.", "어제"),
            ChatItem(5, "천세욱1", "여긴 어디? 난 누구?", "어제"),
            ChatItem(6, "천세욱2", "여긴 어디? 난 누구?", "어제")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF181818))
            .padding(bottom = 56.dp) // 하단 네비게이션 고려
    ) {
        // 상단 스토리/친구 아바타 영역 (간단히)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Gray)
                    .clickable { /* 친구 추가 */ },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.lantern_image),
                    contentDescription = "Add Friend",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            repeat(4) {
                Image(
                    painter = painterResource(id = R.drawable.lantern_image),
                    contentDescription = "Profile",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Yellow)
                        .padding(2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
        // 검색창
        OutlinedTextField(
            value = "",
            onValueChange = {},
            placeholder = { Text("친구검색", color = Color.Gray) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                backgroundColor = Color(0xFF232323), // ✅ material에서는 backgroundColor 사용
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color(0xFF444444),
                textColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        // 채팅 목록
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(chatList) { chat ->
                ChatListItem(chat)
            }
        }
    }
}

@Composable
fun ChatListItem(chat: ChatItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* 채팅방 이동 */ }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.lantern_image),
                    contentDescription = "Profile",
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Yellow)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chat.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = chat.lastMessage,
                color = Color.Gray,
                fontSize = 14.sp,
                maxLines = 1
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = chat.time,
                color = Color.Gray,
                fontSize = 12.sp
            )
            if (chat.unread) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFFFFC107), CircleShape)
                )
            }
        }
    }
    Divider(color = Color(0xFF232323), thickness = 1.dp)
}

@Preview(showBackground = true)
@Composable
fun ChatListScreenPreview() {
    LanternTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            ChatListScreen()
        }
    }
}
