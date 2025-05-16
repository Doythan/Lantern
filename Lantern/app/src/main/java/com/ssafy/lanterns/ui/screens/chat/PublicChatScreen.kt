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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 메시지 데이터 모델
data class ChatMessage(
    val id: Int,
    val sender: String,
    val text: String,
    val time: Long,
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
    paddingValues: PaddingValues = PaddingValues()
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val shouldShowScrollToBottom by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
    var showUsersModal by remember { mutableStateOf(false) }
    var messageInput by remember { mutableStateOf("") }
    
    // 메시지 전송 가능 상태 (딜레이를 위한 상태 추가)
    var canSendMessage by remember { mutableStateOf(true) }
    
    val nearbyUsers = remember {
        listOf(
            ChatUser(1, "김싸피", 80f, 5f),
            ChatUser(2, "이테마", 150f, 10f),
            ChatUser(3, "박비트", 250f, 3f),
            ChatUser(4, "최앱", 300f, 7f),
            ChatUser(5, "정리액트", 350f, 2f),
            ChatUser(6, "한고민", 400f, 8f)
        )
    }
    
    var messages by remember {
        mutableStateOf(
            listOf(
                ChatMessage(1, "시스템", "모두의 광장에 오신 것을 환영합니다. 주변 사람들과 자유롭게 대화해보세요!", System.currentTimeMillis() - 3600000, false),
                ChatMessage(2, "김싸피", "안녕하세요! 반갑습니다~ 오늘 날씨가 정말 좋네요", System.currentTimeMillis() - 1800000, false),
                ChatMessage(3, "이테마", "네 맞아요! 오늘 같은 날은 산책하기 좋을 것 같아요", System.currentTimeMillis() - 1500000, false),
                ChatMessage(4, "박비트", "저는 지금 카페에서 코딩하고 있어요 ☕", System.currentTimeMillis() - 1200000, false),
                ChatMessage(5, "최앱", "와~ 저도 노트북 들고 카페 가려고 했는데! 어느 카페인가요?", System.currentTimeMillis() - 900000, false),
                ChatMessage(6, "정리액트", "여기 사람들 다 개발자인가요?", System.currentTimeMillis() - 600000, false),
                ChatMessage(7, "한고민", "저는 디자이너입니다~ 혹시 협업할 개발자 구하시나요?", System.currentTimeMillis() - 300000, false)
            )
        )
    }
    
    fun sendMessage() {
        if (messageInput.isBlank() || !canSendMessage) return
        
        val newMessage = ChatMessage(
            messages.size + 1,
            "나", // 사용자 이름
            messageInput.trim(),
            System.currentTimeMillis(),
            true // 내가 보낸 메시지임을 표시
        )
        
        messages = messages + newMessage
        messageInput = ""
        
        // 메시지 전송 후 딜레이 설정 (1.5초)
        canSendMessage = false
        coroutineScope.launch {
            delay(1500) // 1.5초 딜레이
            canSendMessage = true
        }
    }
    
    // 메시지 스크롤 효과
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
    
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .imePadding() // 키보드가 올라올 때 입력 영역이 키보드 위로 올라오도록 설정
            .systemBarsPadding() // 시스템 바(상태바, 내비게이션 바)를 고려한 패딩 적용
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 탑 앱바
                TopAppBar(
                    title = {
                        Text(
                            text = "모두의 광장",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "뒤로가기",
                                tint = MaterialTheme.colorScheme.onBackground
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
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = nearbyUsers.size.toString(),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    scrollBehavior = scrollBehavior
                )
                
                // 메시지 목록
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 8.dp),
                    state = listState,
                    reverseLayout = true, // DirectChatScreen과 일관되게 설정
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(messages.reversed()) { msg -> // 메시지를 역순으로 표시
                        ChatMessageBubble(
                            senderName = if (msg.isMe) "나" else msg.sender,
                            text = msg.text,
                            time = formatTime(msg.time),
                            isMe = msg.isMe,
                            senderProfileId = msg.senderProfileId,
                            navController = navController,
                            distance = msg.distance,
                            chatBubbleColor = if (msg.isMe) ChatBubbleMine else ChatBubbleOthers,
                            textColor = MaterialTheme.colorScheme.onBackground,
                            metaTextColor = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                // 메시지 입력창 영역
                PublicChatInputRow(
                    message = messageInput,
                    onMessageChange = { messageInput = it },
                    onSendClick = { sendMessage() },
                    isSendEnabled = canSendMessage
                )
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
}

@Composable
fun PublicChatInputRow(
    message: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSendEnabled: Boolean = true
) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = message,
                onValueChange = onMessageChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("메시지 입력...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                trailingIcon = {
                    IconButton(
                        onClick = onSendClick,
                        enabled = message.isNotBlank() && isSendEnabled
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "전송",
                            tint = if (message.isNotBlank() && isSendEnabled) 
                                   MaterialTheme.colorScheme.secondary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PublicChatScreenPreview() {
    LanternsTheme {
        PublicChatScreen(navController = NavController(LocalContext.current))
    }
}

// 타임스탬프를 "오전 10:30" 형식으로 변환하는 함수
private fun formatTime(timestamp: Long): String {
    val calendar = java.util.Calendar.getInstance().apply {
        timeInMillis = timestamp
    }
    val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
    val minute = calendar.get(java.util.Calendar.MINUTE)
    
    val amPm = if (hour < 12) "오전" else "오후"
    val hour12 = if (hour == 0 || hour == 12) 12 else hour % 12
    
    return "$amPm ${hour12}:${minute.toString().padStart(2, '0')}"
}
