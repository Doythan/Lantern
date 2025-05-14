package com.ssafy.lanterns.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ssafy.lanterns.ui.components.ChatMessageBubble
import com.ssafy.lanterns.ui.components.ChatUser
import com.ssafy.lanterns.ui.components.NearbyUsersModal
import com.ssafy.lanterns.ui.theme.*

// 메시지 데이터 모델
data class Message(
    val id: Int,
    val sender: String,
    val text: String,
    val time: String,
    val isMe: Boolean = false,
    val senderProfileId: Int? = null,
    val distance: Float = 50f // 거리 기반으로 변경 (미터 단위)
)

/**
 * BLE를 이용한 공용 채팅 구현 주석
 * 
 * 공용 채팅에서는 BLE를 통해 다음과 같은 기능을 구현할 수 있습니다:
 * 
 * 1. 광고(Advertising): 
 *    - 사용자 정보와 함께 광고 신호를 보내 주변에 자신의 존재를 알림
 *    - 사용자 ID, 이름, 프로필 이미지 정보 등을 페이로드에 포함
 * 
 * 2. 스캔(Scanning):
 *    - 주변의 광고 신호를 스캔하여 다른 사용자 탐색
 *    - 신호 강도(RSSI)를 통해 상대적 거리 계산
 *    - 스캔 결과를 NearbyUsersModal에 표시
 * 
 * 3. GATT 서버:
 *    - 메시지 특성(Characteristic)을 포함한 서비스 제공
 *    - 다른 기기가 연결하여 메시지를 주고받을 수 있도록 함
 * 
 * 4. GATT 클라이언트:
 *    - 탐색된 기기와 연결하여 메시지 교환
 *    - 연결된 모든 기기에 메시지 브로드캐스트 가능
 * 
 * 구현 아키텍처:
 * - 중앙 관리자 기기 없이 P2P 방식으로 통신
 * - 메시지 전송 시 연결된 모든 기기에 브로드캐스트
 * - 메시지는 임시 ID와 함께 전송하여 중복 수신 방지
 * - 사용자 접근성에 따라 메시지 필터링 가능 (거리 기반)
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicChatScreen(
    navController: NavController,
    paddingValues: PaddingValues = PaddingValues(0.dp)
) {
    // 초기 메시지 목록
    val messages = remember {
        mutableStateListOf(
            Message(1, "도경원", "안녕. 나는 도경원이야.", "10:21 PM", true, senderProfileId = 1, distance = 0f),
            Message(2, "도경원2", "엥. 나도 도경원인데? 너 누구야?? ㅡㅡ", "10:21 PM", false, senderProfileId = 2, distance = 50f),
            Message(3, "도경원", "내가 진짜 도경원이어야", "10:22 PM", true, senderProfileId = 1, distance = 0f),
            Message(4, "여자친구", "너희 둘 도대체 뭐하는 거야?", "10:23 PM", false, senderProfileId = 3, distance = 250f),
            Message(5, "도경원2", "머피떼이뢰!", "10:24 PM", false, senderProfileId = 2, distance = 50f)
        )
    }
    
    // 주변 사용자 목록
    val nearbyUsers = remember {
        listOf(
            ChatUser(1, "도경원", 0f, 3f),
            ChatUser(2, "도경원2", 50f, 5f),
            ChatUser(3, "여자친구", 250f, 12f),
            ChatUser(4, "친구1", 75f, 4f),
            ChatUser(5, "친구2", 350f, 15f)
        )
    }
    
    // 상태 변수
    var messageInput by remember { mutableStateOf("") }
    var showUsersModal by remember { mutableStateOf(false) }
    
    // 스크롤 상태
    val listState = rememberLazyListState()
    val scrollToBottom by remember { derivedStateOf { messages.isNotEmpty() } }
    
    // 새 메시지가 추가되면 자동 스크롤
    LaunchedEffect(scrollToBottom, messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    // 스크롤 동작을 위한 설정
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    
    Surface(
        modifier = Modifier
            .fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(NavyTop, NavyBottom)
                    )
                )
                .padding(paddingValues)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            // 앱 바
            TopAppBar(
                title = { Text("공용 채팅", color = TextWhite) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로 가기",
                            tint = TextWhite
                        )
                    }
                },
                actions = {
                    // 참여자 수 아이콘
                    IconButton(onClick = { showUsersModal = true }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.People,
                                contentDescription = "참여자 목록",
                                tint = BleAccent
                            )
                            Text(
                                text = nearbyUsers.size.toString(),
                                fontSize = 14.sp,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyTop,
                    titleContentColor = TextWhite
                ),
                scrollBehavior = scrollBehavior
            )
            
            // 메시지 목록
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(NavyTop, NavyBottom)
                        )
                    )
                    .padding(horizontal = 8.dp),
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages) { msg ->
                    ChatMessageBubble(
                        senderName = msg.sender,
                        text = msg.text,
                        time = msg.time,
                        isMe = msg.isMe,
                        senderProfileId = msg.senderProfileId,
                        navController = navController,
                        distance = msg.distance
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            // 메시지 입력창 영역
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = ChatInputBackground
                ),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 메시지 입력 필드
                    TextField(
                        value = messageInput,
                        onValueChange = { messageInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        placeholder = { 
                            Text(
                                text = "메시지를 입력하세요",
                                color = TextWhite70
                            ) 
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = BleAccent
                        ),
                        maxLines = 3,
                        shape = RoundedCornerShape(24.dp)
                    )
                    
                    // 전송 버튼
                    IconButton(
                        onClick = {
                            if (messageInput.isNotEmpty()) {
                                val newMessage = Message(
                                    id = messages.size + 1,
                                    sender = "도경원",
                                    text = messageInput,
                                    time = "지금",
                                    isMe = true,
                                    senderProfileId = 1,
                                    distance = 0f
                                )
                                messages.add(newMessage)
                                messageInput = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(BleBlue1, BleAccent)
                                )
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "전송",
                            tint = TextWhite
                        )
                    }
                }
            }
        }
        
        // 주변 사용자 목록 모달
        AnimatedVisibility(
            visible = showUsersModal,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            NearbyUsersModal(
                users = nearbyUsers,
                onDismiss = { showUsersModal = false },
                navController = navController
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PublicChatScreenPreview() {
    LanternTheme {
        val dummyNavController = NavController(LocalContext.current)
        PublicChatScreen(navController = dummyNavController)
    }
}
