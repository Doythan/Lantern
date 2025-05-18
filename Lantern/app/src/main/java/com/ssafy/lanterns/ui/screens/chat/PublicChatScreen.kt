package com.ssafy.lanterns.ui.screens.chat

import android.app.Activity
import android.util.Log
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ssafy.lanterns.utils.PermissionHelper
import com.ssafy.lanterns.service.ble.advertiser.AdvertiserManager
import com.ssafy.lanterns.service.ble.scanner.ScannerManager
import com.ssafy.lanterns.ui.components.ChatMessageBubble
import com.ssafy.lanterns.ui.components.ChatUser
import com.ssafy.lanterns.ui.components.NearbyUsersModal
import com.ssafy.lanterns.ui.theme.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.consumeWindowInsets

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
    paddingValues: PaddingValues = PaddingValues(0.dp),
    viewModel: PublicChatScreenViewModel = hiltViewModel()
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val shouldShowScrollToBottom by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
    var showUsersModal by remember { mutableStateOf(false) }
    var messageInput by remember { mutableStateOf("") }
    
    // 메시지 전송 가능 상태 (딜레이를 위한 상태 추가)
    var canSendMessage by remember { mutableStateOf(true) }
    
    // 주변 사용자 목록
    val nearbyUsers = remember { mutableStateListOf<ChatUser>() }
    
    val currentUser by viewModel.currentUser
    val messages by viewModel.messages
    
    // 객체 생성
    val context = LocalContext.current
    
    // 메시지 전송 함수 정의
    fun sendMessage() {
        if (messageInput.isBlank() || !canSendMessage) return
        
        val senderName = currentUser?.nickname ?: "나"
        
        val newMessage = ChatMessage(
            viewModel.getNextMessageId(),
            senderName,
            messageInput.trim(),
            System.currentTimeMillis(),
            true // 내가 보낸 메시지임을 표시
        )
        
        // 메시지 추가 (UI에 메시지 표시)
        viewModel.addMessage(newMessage)
        
        // 메시지 바이트 길이 제한 확인 및 분할
        val splitList = splitMessageByByteLength(messageInput)
        
        // 로그 확인
        Log.d("PublicChat", "메시지 전송: $messageInput, 분할된 메시지: ${splitList.size}개 패킷")
        
        // BLE 광고 시작 - 메시지 브로드캐스트
        AdvertiserManager.startAdvertising(
            messageList = splitList,
            email = senderName,
            activity = context as Activity,
            state = 0
        )
        
        // 입력란 초기화
        messageInput = ""
        
        // 메시지 전송 후 딜레이 설정 (1.5초)
        canSendMessage = false
        coroutineScope.launch {
            delay(1500) // 1.5초 딜레이
            canSendMessage = true
        }
    }
    
    LaunchedEffect(Unit) {
        // PermissionHelper 객체 생성
        val permissionHelper = PermissionHelper(context as Activity)
        ScannerManager.init(context as Activity)
        AdvertiserManager.init(context as Activity)

        // 권한이 없다면 요청
        if(!permissionHelper.hasPermission()) permissionHelper.requestPermissions(1001);
        // 있다면
        else{
            // 블루투스를 사용자가 켰는지 확인
            if(permissionHelper.isBluetoothEnabeld()) {
                // 스캔 시작 - 메시지와 사용자 정보 수신
                ScannerManager.startScanning(context){ sender, text ->
                    // 메시지 수신 처리
                    val newMessage = ChatMessage(
                        id = viewModel.getNextMessageId(),
                        sender = sender,
                        text = text,
                        time = System.currentTimeMillis(),
                        isMe = false,
                        senderProfileId = null,
                        distance = 0f
                    )
                    viewModel.addMessage(newMessage)
                    
                    // 발신자가 주변 사용자 목록에 없으면 추가
                    if (sender != "Unknown" && nearbyUsers.none { it.name == sender }) {
                        nearbyUsers.add(
                            ChatUser(
                                id = nearbyUsers.size + 1,
                                name = sender,
                                distance = 100f, // 기본 거리
                                messageCount = 1f // 기본 메시지 개수
                            )
                        )
                    }
                }
            }
            else Log.d("1234", "블루투스가 활성화되지 않았습니다.")
        }

        // 초기 메시지 설정 (ViewModel)
        viewModel.initializeDefaultMessages()
    }

    // 화면이 사라질 때 광고/스캔 정지
    DisposableEffect(Unit) {
        onDispose {
            Log.d("Compose", "💨 PublicChatScreen dispose - stopping BLE")
            AdvertiserManager.stopAdvertising()
            ScannerManager.stopScanning()
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
            .statusBarsPadding(), // systemBarsPadding() 대신 statusBarsPadding()만 적용
        contentColor = MaterialTheme.colorScheme.onBackground,
        color = MaterialTheme.colorScheme.background
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
                                    // 현재 사용자(나) + 다른 주변 사용자 표시
                                    text = (nearbyUsers.size + 1).toString(),
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
                    isSendEnabled = canSendMessage,
                    modifier = Modifier
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
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .navigationBarsPadding() // 내비게이션 바 패딩 유지
            .imePadding() // exclude 대신 단순 imePadding() 적용
    ) {
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

// 현재 날짜 함수
fun getCurrentTimeFormatted(): String {
    val now = LocalDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    return now.format(formatter)
}

// 메시지를 바이트 단위로 분할하는 함수
fun splitMessageByByteLength(message: String, maxBytes: Int = 17): List<String> {
    val result = mutableListOf<String>()
    var current = ""
    var currentBytes = 0

    for (char in message) {
        val charBytes = char.toString().toByteArray(Charsets.UTF_8)
        if (currentBytes + charBytes.size > maxBytes) {
            result.add(current)
            current = ""
            currentBytes = 0
        }
        current += char
        currentBytes += charBytes.size
    }

    if (current.isNotEmpty()) {
        result.add(current)
    }

    return result
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