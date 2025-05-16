package com.ssafy.lanterns.ui.screens.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.ssafy.lanterns.data.model.User
import com.ssafy.lanterns.ui.components.ChatMessageBubble
import com.ssafy.lanterns.ui.theme.*
import android.widget.Toast
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

// 더미 메시지 데이터 모델
data class DirectMessage(
    val id: Int,
    val sender: String,
    val text: String,
    val time: String,
    val isMe: Boolean = false,
    val senderProfileId: Int? = null
)

/**
 * BLE를 이용한 1대1 채팅 구현 주석
 * 
 * 1대1 채팅에서는 다음과 같은 BLE 기능을 활용할 수 있습니다:
 * 
 * 1. 타겟 기기 선택:
 *    - 사용자 목록에서 선택하거나 QR 코드를 통해 직접 연결
 *    - 블루투스 주소나 고유 식별자를 사용하여 특정 기기와 통신
 * 
 * 2. 연결 관리:
 *    - 지정된 기기와의 GATT 연결 유지
 *    - 연결 상태 모니터링 및 자동 재연결 메커니즘
 * 
 * 3. 데이터 보안:
 *    - 1대1 통신에 적합한 보안 메커니즘 구현
 *    - 페어링 또는 암호화 키 교환 가능
 * 
 * 4. 메시지 동기화:
 *    - 오프라인 메시지 저장 및 연결 시 동기화
 *    - 메시지 수신 확인 메커니즘
 * 
 * 5. 연결 최적화:
 *    - 배터리 소모 최소화를 위한 연결 파라미터 조정
 *    - 양방향 통신에 최적화된 BLE 설정
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun DirectChatScreen(
    userId: String,
    navController: NavController,
    viewModel: DirectChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var messageInput by remember { mutableStateOf("") }
    
    // 스크롤 상태 및 코루틴 스코프
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // 메시지 전송 가능 상태 (딜레이를 위한 상태 추가)
    var canSendMessage by remember { mutableStateOf(true) }
    
    // 자동 스크롤 필요 여부 확인 (첫 번째 아이템이 보이지 않을 때)
    val isAtBottom by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 || uiState.messages.isEmpty()
        }
    }
    
    // "맨 아래로" 버튼 표시 여부
    val showScrollToBottomButton by remember {
        derivedStateOf {
            !isAtBottom && listState.firstVisibleItemIndex > 3
        }
    }
    
    // 마지막 메시지 ID 추적 (새 메시지 감지용)
    var lastMessageId by remember { mutableStateOf(uiState.messages.firstOrNull()?.id ?: -1) }
    var messageCountFromLastScroll by remember { mutableStateOf(0) }
    
    // 무한 스크롤을 위한 첫 번째 보이는 아이템 감지
    val shouldLoadMore by remember {
        derivedStateOf {
            val firstVisibleItem = listState.firstVisibleItemIndex
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems > 0 && firstVisibleItem >= totalItems - 5 && !uiState.isLoadingMore && uiState.hasMoreMessages
        }
    }
    
    // 새 메시지가 추가되었는지 확인
    LaunchedEffect(uiState.messages) {
        val currentFirstMessage = uiState.messages.firstOrNull()
        if (currentFirstMessage != null && lastMessageId != currentFirstMessage.id) {
            if (isAtBottom || messageInput.isNotEmpty()) {
                if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                     delay(100) 
                     listState.animateScrollToItem(0)
                }
            } else {
                messageCountFromLastScroll++
            }
            lastMessageId = currentFirstMessage.id
        } else if (currentFirstMessage == null) {
            lastMessageId = -1
        }
    }
    
    // 무한 스크롤 - 추가 메시지 로드
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMoreMessages()
        }
    }
    
    // 최초 로드 시 또는 로드 후 스크롤
    LaunchedEffect(Unit) {
        if (uiState.messages.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }
    
    // 스크롤 동작을 위한 설정
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    
    // 오류 메시지 처리
    val context = LocalContext.current
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearErrorMessage()
        }
    }
    
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .imePadding() // 키보드가 올라올 때 입력 영역이 키보드 위로 올라오도록 설정
            .systemBarsPadding(), // 시스템 바(상태바, 내비게이션 바)를 고려한 패딩 적용
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        // 상대방 닉네임을 명확하게 표시
                        text = uiState.participant?.nickname ?: "채팅", 
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로 가기",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* 통화 액션 */ }) {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = "통화하기",
                            tint = MaterialTheme.colorScheme.onSurface 
                        )
                    }
                    IconButton(onClick = { /* 추가 옵션 */ }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "더보기",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface, 
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp) 
                ),
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            DirectMessageInputRow(
                message = messageInput,
                onMessageChange = { messageInput = it },
                onSendClick = {
                    if (messageInput.isNotBlank() && canSendMessage) {
                        viewModel.sendMessage(messageInput)
                        messageInput = ""
                        coroutineScope.launch { 
                            listState.animateScrollToItem(0)
                        }
                        
                        // 메시지 전송 후 딜레이 설정 (1.5초)
                        canSendMessage = false
                        coroutineScope.launch {
                            delay(1500) // 1.5초 딜레이
                            canSendMessage = true
                        }
                    }
                },
                isSendEnabled = canSendMessage
            )
        }
    ) { innerPadding -> 
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                state = listState,
                reverseLayout = true, 
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    ChatMessageBubble(
                        senderName = if (!message.isMe) message.sender else "나",
                        text = message.text,
                        time = message.time,
                        isMe = message.isMe,
                        senderProfileId = if (!message.isMe) message.senderProfileId else null,
                        navController = navController
                    )
                }
            }

            AnimatedVisibility(
                visible = showScrollToBottomButton,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                Button(
                    onClick = {
                        coroutineScope.launch { listState.animateScrollToItem(0) }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LanternYellow,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "맨 아래로 스크롤")
                    Spacer(Modifier.width(4.dp))
                    Text("맨 아래로")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectMessageInputRow(
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

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun DirectChatScreenPreview() {
    // 현재 DirectChatViewModel이 SavedStateHandle을 통해 userId를 받으므로,
    // Preview에서 Hilt ViewModel을 직접 생성하기 어렵습니다.
    // 해결책 1: ViewModel 로직을 모방한 가짜 ViewModel을 만들거나,
    // 해결책 2: UiState를 직접 파라미터로 받는 Content Composable을 만듭니다.
    // 여기서는 화면의 전체적인 구성을 보기 위해 일단 NavController만 가짜로 전달합니다.
    // 실제 데이터는 표시되지 않거나, 기본값으로 표시될 것입니다.
    // (ChatListScreen에서 사용한 Content 분리 패턴을 적용하는 것이 더 좋습니다.)

    val dummyNavController = rememberNavController()
    // 가짜 User 객체 생성
    val fakeCurrentUser = User(userId = 0, nickname = "나", email = "me@example.com", lanterns = 10, 
        deviceId = "fake_device_id", profileImage = null, token = "", refreshToken = "", 
        isAuthenticated = true, createdAt = java.time.LocalDateTime.now())
    val fakeParticipant = User(userId = 1, nickname = "상대방", email = "other@example.com", lanterns = 5, 
        deviceId = "fake_device_id2", profileImage = null, token = "", refreshToken = "", 
        isAuthenticated = true, createdAt = java.time.LocalDateTime.now())

    val sampleMessages = listOf(
        DirectMessage(1, fakeCurrentUser.nickname, "안녕하세요!", "10:00", true, fakeCurrentUser.userId.toInt()),
        DirectMessage(2, fakeParticipant.nickname, "네, 안녕하세요!", "10:01", false, fakeParticipant.userId.toInt()),
        DirectMessage(3, fakeCurrentUser.nickname, "오늘 날씨가 좋네요.", "10:02", true, fakeCurrentUser.userId.toInt()),
        DirectMessage(4, fakeParticipant.nickname, "그러게요. 산책하기 좋은 날씨입니다.", "10:03", false, fakeParticipant.userId.toInt()),
        DirectMessage(5, fakeCurrentUser.nickname, "점심은 뭐 드실 거예요? 같이 식사하실래요?", "10:04", true, fakeCurrentUser.userId.toInt()),
        DirectMessage(6, fakeParticipant.nickname, "좋죠! 뭐 먹을까요? 파스타 어때요?", "10:05", false, fakeParticipant.userId.toInt()),
        DirectMessage(7, fakeCurrentUser.nickname, "파스타 좋아요! 근처에 맛집 아는 곳 있어요?", "10:06", true, fakeCurrentUser.userId.toInt()),
        DirectMessage(8, fakeParticipant.nickname, "네, 알죠! 제가 안내할게요. 12시에 정문 앞에서 만나요.", "10:07", false, fakeParticipant.userId.toInt()),
        DirectMessage(9, fakeCurrentUser.nickname, "알겠습니다. 그때 뵐게요!", "10:08", true, fakeCurrentUser.userId.toInt())
    ).reversed() // 최신 메시지가 아래로 가도록

    val mockUiState = DirectChatUiState(
        isLoading = false,
        messages = sampleMessages,
        participant = fakeParticipant,
        chatRoomId = 1L,
        errorMessage = null,
        connectionStatus = "연결됨",
        isConnecting = false,
        isLoadingMore = false,
        hasMoreMessages = true,
        signalStrength = 80
    )
    
    // DirectChatScreen의 Content 부분을 별도의 Composable로 분리했다면
    // 그 Composable을 여기서 호출하는 것이 이상적입니다.
    // 예: DirectChatScreenContent(uiState = mockUiState, navController = dummyNavController, ...)

    // 현재 구조에서는 ViewModel을 직접 주입하거나 hiltViewModel()을 사용해야 합니다.
    // Preview의 한계로 인해 실제 ViewModel 로직이 동작하지 않을 수 있습니다.
    // 가장 좋은 방법은 DirectChatScreen 내부의 Scaffold Content를 분리하는 것입니다.
    // 아래는 임시로 UI 상태를 직접 전달하는 가상의 Content 함수를 호출하는 예시입니다.
    // 실제 코드에서는 DirectChatScreen 내부의 Scaffold Content를 분리해야 합니다.

    LanternsTheme {
        // DirectChatScreen(userId = "1", navController = dummyNavController) // Hilt ViewModel 사용 시 Preview 어려움
        // 아래는 ChatListScreen처럼 Content Composable을 분리했다고 가정하고 호출하는 방식입니다.
        // 실제 DirectChatScreen 코드를 수정하여 Content Composable을 만들고,
        // DirectChatScreen 함수는 해당 Content Composable을 호출하도록 변경해야 합니다.
        DirectChatScreenContentForPreview(
            uiState = mockUiState,
            messageInput = "미리보기 메시지",
            onMessageChange = {},
            onSendClick = {},
            listState = rememberLazyListState(),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            navController = dummyNavController,
            currentUserId = fakeCurrentUser.userId,
            showScrollToBottomButton = false,
            onScrollToBottom = {}
        )
    }
}

// Preview를 위한 가짜 Content Composable (DirectChatScreen의 Scaffold 내부를 여기에 옮겨야 함)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectChatScreenContentForPreview(
    uiState: DirectChatUiState,
    messageInput: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scrollBehavior: TopAppBarScrollBehavior,
    navController: NavController,
    currentUserId: Long?,
    showScrollToBottomButton: Boolean,
    onScrollToBottom: () -> Unit
) {
    val currentUser = User(userId = currentUserId ?: 0L, nickname = "나", deviceId = "", statusMessage = null)
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        uiState.participant?.nickname ?: "채팅",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로 가기",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // IconButton(onClick = { /* 통화 액션 */ }) {
                    //     Icon(
                    //         Icons.Default.Call,
                    //         contentDescription = "통화하기",
                    //         tint = MaterialTheme.colorScheme.onSurface
                    //     )
                    // }
                    // IconButton(onClick = { /* 추가 옵션 */ }) {
                    //     Icon(
                    //         Icons.Default.MoreVert,
                    //         contentDescription = "더보기",
                    //         tint = MaterialTheme.colorScheme.onSurface
                    //     )
                    // }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                ),
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            DirectMessageInputRow(
                message = messageInput,
                onMessageChange = onMessageChange,
                onSendClick = onSendClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 메시지 목록
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    ChatMessageBubble(
                        senderName = if (message.isMe) currentUser.nickname else uiState.participant?.nickname ?: message.sender,
                        text = message.text,
                        time = message.time,
                        isMe = message.isMe,
                        senderProfileId = if (message.isMe) currentUser.userId.toInt() else uiState.participant?.userId?.toInt(),
                        navController = navController
                    )
                }
                if (uiState.isLoadingMore) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }

            // "맨 아래로" 버튼 (구현 필요 시 추가)
            // AnimatedVisibility(
            //     visible = showScrollToBottomButton,
            //     enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            //     exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            //     modifier = Modifier.align(Alignment.CenterHorizontally)
            // ) {
            //     FloatingActionButton(
            //         onClick = onScrollToBottom,
            //         modifier = Modifier.padding(bottom = 8.dp),
            //         containerColor = MaterialTheme.colorScheme.secondaryContainer,
            //         contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            //     ) {
            //         Icon(Icons.Default.KeyboardArrowDown, "맨 아래로")
            //     }
            // }
        }
    }
}
