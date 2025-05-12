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
import androidx.compose.material.icons.filled.SignalCellular0Bar
import androidx.compose.material.icons.filled.SignalCellular4Bar
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.ssafy.lanterns.ui.components.ChatMessageBubble
import com.ssafy.lanterns.ui.theme.*
import android.widget.Toast
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    
    // 확장 메뉴 상태 - 변수 선언을 사용 전으로 이동
    var showExpandedMenu by remember { mutableStateOf(false) }
    
    // 스크롤 상태 및 코루틴 스코프
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // 자동 스크롤 필요 여부 확인 (첫 번째 아이템이 보이지 않을 때)
    val isAtBottom by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0
        }
    }
    
    // "맨 아래로" 버튼 표시 여부
    val showScrollToBottomButton by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 3 // 상단에서 3개 이상 항목이 지나갔을 때
        }
    }
    
    // 마지막 메시지 ID 추적 (새 메시지 감지용)
    var lastMessageId by remember { mutableStateOf(-1) }
    var messageCountFromLastScroll by remember { mutableStateOf(0) }
    
    // 무한 스크롤을 위한 첫 번째 보이는 아이템 감지
    val shouldLoadMore by remember {
        derivedStateOf {
            val firstVisibleItem = listState.firstVisibleItemIndex
            // 상단에 가까워졌을 때 (예: 첫 5개 아이템 중 하나가 보일 때) 더 많은 메시지 로드
            firstVisibleItem < 5 && uiState.messages.isNotEmpty() && !uiState.isLoadingMore && uiState.hasMoreMessages
        }
    }
    
    // 새 메시지가 추가되었는지 확인
    LaunchedEffect(uiState.messages) {
        if (uiState.messages.isNotEmpty()) {
            val currentFirstMessageId = uiState.messages.firstOrNull()?.id ?: -1
            if (lastMessageId != currentFirstMessageId && lastMessageId != -1) {
                // 새 메시지가 추가됨
                if (isAtBottom) {
                    // 사용자가 이미 맨 아래에 있으면 자동 스크롤
                    delay(100) // 약간의 지연으로 자연스러운 애니메이션 제공
                    listState.animateScrollToItem(0)
                } else {
                    // 사용자가 스크롤 중이면 메시지 카운트 증가
                    messageCountFromLastScroll++
                }
            }
            lastMessageId = currentFirstMessageId
        }
    }
    
    // 무한 스크롤 - 추가 메시지 로드
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMoreMessages()
        }
    }
    
    // 최초 로드 시 또는 로드 후 스크롤
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && uiState.messages.isNotEmpty()) {
            listState.scrollToItem(0)
            messageCountFromLastScroll = 0
        }
    }
    
    // 스크롤 동작을 위한 설정
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    
    // 오류 메시지 처리
    val context = LocalContext.current
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }
    
    // 플러스 버튼 회전 애니메이션
    val rotation by animateFloatAsState(
        targetValue = if (showExpandedMenu) 45f else 0f,
        animationSpec = tween(300),
        label = "plusRotation"
    )
    
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = uiState.participant?.nickname ?: "사용자",
                                color = TextWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            val (signalText, signalColor) = when {
                                uiState.signalStrength > 70 -> Pair("연결 강함", ConnectionNear)
                                uiState.signalStrength > 30 -> Pair("연결 중간", ConnectionMedium)
                                else -> Pair("연결 약함", ConnectionFar)
                            }
                            
                            Text(
                                text = signalText,
                                color = signalColor,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .background(
                                        color = signalColor.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        viewModel.exitChat()
                        navController.popBackStack() 
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite
                        )
                    }
                },
                actions = { IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = TextWhite
                    )
                } },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextWhite,
                    navigationIconContentColor = TextWhite,
                    actionIconContentColor = TextWhite
                )
            )
        },
        containerColor = NavyTop,
        floatingActionButton = { }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(NavyTop, NavyBottom)
                    )
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 채팅 메시지 목록
                Box(modifier = Modifier.weight(1f)) {
                    // 로딩 표시 (초기 로딩)
                    if (uiState.isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = BleAccent)
                        }
                    } else {
                        // 메시지 목록
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            state = listState,
                            reverseLayout = true
                        ) {
                            // 더 로드 중일 때 로딩 인디케이터 표시
                            if (uiState.isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(30.dp),
                                            color = BleAccent,
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                            
                            // 더 이상 메시지가 없을 때 표시
                            
                            
                            // 메시지 목록
                            items(uiState.messages) { msg ->
                                ChatMessageBubble(
                                    senderName = msg.sender,
                                    text = msg.text,
                                    time = msg.time,
                                    isMe = msg.isMe,
                                    senderProfileId = msg.senderProfileId
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                    
                    // 맨 아래로 스크롤 버튼
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .padding(horizontal = 16.dp)
                    ) {
                        AnimatedContent(
                            targetState = showScrollToBottomButton,
                            transitionSpec = {
                                ContentTransform(
                                    targetContentEnter = fadeIn() + expandVertically(),
                                    initialContentExit = fadeOut(),
                                    sizeTransform = null
                                )
                            },
                            modifier = Modifier.align(Alignment.BottomEnd),
                            label = "ScrollButtonAnimation"
                        ) { show ->
                            if (show) {
                                FloatingActionButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(0)
                                            messageCountFromLastScroll = 0
                                        }
                                    },
                                    modifier = Modifier.size(48.dp),
                                    containerColor = BleBlue1,
                                    contentColor = TextWhite
                                ) {
                                    Box {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Scroll to bottom",
                                            tint = TextWhite
                                        )
                                        
                                        if (messageCountFromLastScroll > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(BleAccent)
                                                    .align(Alignment.TopEnd),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (messageCountFromLastScroll > 9) "9+" else messageCountFromLastScroll.toString(),
                                                    color = TextWhite,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // 메시지 입력 영역
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 통화하기 버튼 영역
                    AnimatedVisibility(
                        visible = showExpandedMenu,
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                    ) {
                        Button(
                            onClick = {
                                navController.navigate("outgoingcall/${uiState.participant?.userId}") {
                                    launchSingleTop = true
                                }
                                showExpandedMenu = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BleAccent
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .height(48.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "통화",
                                    tint = TextWhite,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "통화하기", 
                                    color = TextWhite,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    
                    // 메시지 입력 행
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 플러스 버튼 (메시지 입력창 왼쪽)
                        IconButton(
                            onClick = { showExpandedMenu = !showExpandedMenu },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(BleBlue1, BleBlue2.copy(alpha = 0.7f))
                                    )
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = if (showExpandedMenu) "닫기" else "기능 추가",
                                tint = TextWhite,
                                modifier = Modifier.rotate(rotation)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // 메시지 입력 필드
                        TextField(
                            value = messageInput,
                            onValueChange = { messageInput = it },
                            placeholder = { Text("메시지를 입력하세요", color = TextWhite70) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(24.dp)),
                            textStyle = TextStyle(
                                color = TextWhite,
                                fontSize = 16.sp
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = ChatInputBackground,
                                unfocusedContainerColor = ChatInputBackground,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = BleAccent
                            ),
                            singleLine = false,
                            maxLines = 4
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // 전송 버튼
                        IconButton(
                            onClick = { 
                                if (messageInput.isNotBlank()) {
                                    viewModel.sendMessage(messageInput)
                                    messageInput = ""
                                    
                                    // 메시지 전송 후 바로 맨 아래로 스크롤
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(0)
                                        messageCountFromLastScroll = 0
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(BleBlue1, BleAccent)
                                    )
                                ),
                            enabled = messageInput.isNotBlank() && !uiState.isConnecting
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun DirectChatScreenPreview() {
    LanternTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = NavyTop
        ) {
            val dummyNavController = NavController(LocalContext.current)
            DirectChatScreen(userId = "1", navController = dummyNavController)
        }
    }
}
