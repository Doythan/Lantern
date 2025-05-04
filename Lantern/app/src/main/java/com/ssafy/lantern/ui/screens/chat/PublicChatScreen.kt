package com.ssafy.lantern.ui.screens.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.ssafy.lantern.ui.theme.LanternTheme
import com.ssafy.lantern.R

// 더미 메시지 데이터 모델
data class Message(
    val id: Int,
    val sender: String,
    val text: String,
    val time: String,
    val isMe: Boolean = false
)

@Composable
fun PublicChatScreen() {
    val messages = remember {
        listOf(
            Message(1, "도경원", "안녕. 나는 도경원이야.", "10:21 PM", true),
            Message(2, "도경원", "엥. 나도 도경원인데? 너 누구야?? ㅡㅡ", "10:21 PM", false),
            Message(3, "도경원", "내가 진짜 도경원이어야", "10:21 PM", true),
            Message(4, "여자친구", "너희 둘 도대체 뭐하는 거야?", "10:21 PM", false)
        )
    }
    val (input, setInput) = remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF181818))
    ) {
        // 상단 AppBar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF181818))
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { /* 뒤로가기 */ }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "공용 채팅",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(id = R.drawable.lantern_image),
                contentDescription = "Option",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        // 메시지 목록
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            messages.forEach { msg ->
                ChatBubble(msg)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        // 하단 입력창
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF232323))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = input,
                onValueChange = setInput,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                modifier = Modifier
                    .weight(1f)
                    .background(Color.Transparent)
                    .padding(8.dp),
                decorationBox = { innerTextField ->
                    if (input.isEmpty()) {
                        Text("메시지 입력", color = Color.Gray, fontSize = 16.sp)
                    }
                    innerTextField()
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFC107))
                    .clickable { /* 메시지 전송 */ },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.lantern_image),
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun ChatBubble(msg: Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isMe) Arrangement.End else Arrangement.Start
    ) {
        if (!msg.isMe) {
            Image(
                painter = painterResource(id = R.drawable.lantern_image),
                contentDescription = "Profile",
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Yellow)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (msg.isMe) Alignment.End else Alignment.Start) {
            Box(
                modifier = Modifier
                    .background(
                        if (msg.isMe) Color(0xFFFFC107) else Color.White,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = msg.text,
                    color = if (msg.isMe) Color.Black else Color.Black,
                    fontSize = 15.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = msg.time,
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
        if (msg.isMe) {
            Spacer(modifier = Modifier.width(8.dp))
            Image(
                painter = painterResource(id = R.drawable.lantern_image),
                contentDescription = "Profile",
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Yellow)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PublicChatScreenPreview() {
    LanternTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            PublicChatScreen()
        }
    }
}
