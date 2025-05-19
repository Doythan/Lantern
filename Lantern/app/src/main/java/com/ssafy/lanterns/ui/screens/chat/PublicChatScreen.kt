package com.ssafy.lanterns.ui.screens.chat

import android.app.Activity
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items // 여기를 itemsIndexed 대신 items로 변경
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding


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
    val shouldShowScrollToBottom by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 && listState.layoutInfo.totalItemsCount > 0 } }
    var showUsersModal by remember { mutableStateOf(false) }
    var messageInput by remember { mutableStateOf("") }
    var canSendMessage by remember { mutableStateOf(true) }

    val nearbyUsers = remember { mutableStateListOf<ChatUser>() }
    val currentUser by viewModel.currentUser
    val messages by viewModel.messages // ViewModel의 messages 상태를 관찰

    val context = LocalContext.current

    fun sendMessage() {
        if (messageInput.isBlank() || !canSendMessage) return

        val senderName = currentUser?.nickname ?: "나" // currentUser가 null이면 "나"로 표시
        val newChatMessage = ChatMessage(
            // id는 ViewModel에서 DB 저장 시 자동 생성되거나 타임스탬프 등으로 관리되므로 여기서는 0으로 전달하거나 ViewModel에서 처리
            id = 0, // 임시 ID 또는 ViewModel에서 생성하도록 변경
            sender = senderName,
            text = messageInput.trim(),
            time = System.currentTimeMillis(),
            isMe = true, // 내가 보낸 메시지
            senderProfileId = currentUser?.selectedProfileImageNumber
        )

        viewModel.addMessage(newChatMessage) // ViewModel을 통해 메시지 추가 (DB 저장 포함)

        // BLE 광고 로직 (기존 유지)
        val splitList = splitMessageByByteLength(messageInput)
        Log.d("PublicChat", "메시지 전송: $messageInput, 분할된 메시지: ${splitList.size}개 패킷")
        AdvertiserManager.startAdvertising(
            messageList = splitList,
            email = senderName,
            activity = context as Activity,
            state = 0
        )

        messageInput = ""
        canSendMessage = false
        coroutineScope.launch {
            delay(1500)
            canSendMessage = true
        }
    }

    LaunchedEffect(Unit) {
        val permissionHelper = PermissionHelper(context as Activity)
        Log.d("PublicChatScreen", "LaunchedEffect 시작")
        ScannerManager.init(context as Activity)
        AdvertiserManager.init(context as Activity)

        val hasPermissionResult = permissionHelper.hasPermission()
        val isBluetoothEnabledResult = permissionHelper.isBluetoothEnabeld()

        Log.d("PublicChatScreen", "권한 상태: $hasPermissionResult, 블루투스 활성화 상태: $isBluetoothEnabledResult")

        if (!hasPermissionResult) {
            Log.d("PublicChatScreen", "권한 없음. 권한 요청 시도.")
            permissionHelper.requestPermissions(1001)
        } else {
            Log.d("PublicChatScreen", "권한 있음.")
            if (isBluetoothEnabledResult) {
                Log.d("PublicChatScreen", "블루투스 활성화됨. 스캔 시작 시도.")
                ScannerManager.startScanning(context as Activity) { sender, text ->
                    val receivedMessage = ChatMessage(
                        id = viewModel.getNextMessageId(), // ViewModel 통해 ID 생성
                        sender = sender,
                        text = text,
                        time = System.currentTimeMillis(),
                        isMe = false,
                        senderProfileId = null, // 필요시 스캔 결과에서 프로필 ID 추출
                        distance = 0f
                    )
                    viewModel.addMessage(receivedMessage) // ViewModel 통해 수신 메시지 추가 (DB 저장 포함)

                    if (sender != "Unknown" && nearbyUsers.none { it.name == sender }) {
                        nearbyUsers.add(
                            ChatUser(
                                id = nearbyUsers.size + 1,
                                name = sender,
                                distance = 100f,
                                messageCount = 1f
                            )
                        )
                    }
                }
            } else {
                Log.d("PublicChatScreen", "블루투스가 활성화되지 않았습니다. 스캔 시작 안함.")
            }
        }
        // ViewModel의 initializeDefaultMessages는 ViewModel의 init 블록에서 호출되므로 여기서 중복 호출 필요 없음
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.d("Compose", "💨 PublicChatScreen dispose - stopping BLE")
            AdvertiserManager.stopAdvertising()
            ScannerManager.stopScanning(context as Activity)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && listState.firstVisibleItemIndex < 2 && listState.firstVisibleItemScrollOffset == 0) { // 자동 스크롤 조건 강화
            coroutineScope.launch {
                listState.animateScrollToItem(0) // 새 메시지 오면 맨 아래로 스크롤 (LazyColumn의 reverseLayout=true)
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .statusBarsPadding(),
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
                                    text = (nearbyUsers.size + (if (currentUser != null) 1 else 0)).toString(), // 나 포함
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

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 8.dp),
                    state = listState,
                    reverseLayout = true, // 새 메시지가 아래에 추가되도록
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(messages, key = { it.time }) { msg -> // id 대신 time을 key로 사용하거나 고유 ID 생성 로직 확인
                        ChatMessageBubble(
                            senderName = if (msg.isMe) currentUser?.nickname ?: "나" else msg.sender,
                            text = msg.text,
                            time = formatTime(msg.time),
                            isMe = msg.isMe,
                            senderProfileId = if(msg.isMe) currentUser?.selectedProfileImageNumber else msg.senderProfileId,
                            navController = navController,
                            distance = msg.distance,
                            chatBubbleColor = if (msg.isMe) ChatBubbleMine else ChatBubbleOthers,
                            textColor = MaterialTheme.colorScheme.onBackground,
                            metaTextColor = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                PublicChatInputRow(
                    message = messageInput,
                    onMessageChange = { messageInput = it },
                    onSendClick = { sendMessage() },
                    isSendEnabled = canSendMessage,
                    modifier = Modifier
                )
            }

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

// PublicChatInputRow, getCurrentTimeFormatted, splitMessageByByteLength, formatTime 함수는 기존과 동일하게 유지

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
            .navigationBarsPadding()
            .imePadding()
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

@Preview(showBackground = true)
@Composable
fun PublicChatScreenPreview() {
    LanternsTheme {
        PublicChatScreen(navController = NavController(LocalContext.current))
    }
}