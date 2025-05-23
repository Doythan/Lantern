package com.ssafy.lanterns.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ssafy.lanterns.data.source.ble.mesh.ChatMessage
import com.ssafy.lanterns.data.source.ble.mesh.NearbyNode
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import com.ssafy.lanterns.ui.screens.chat.BluetoothDisabledWarning
import com.ssafy.lanterns.ui.screens.chat.rememberBluetoothPermissionLauncher
import com.ssafy.lanterns.ui.screens.chat.requestBluetoothPermissions
import com.ssafy.lanterns.ui.screens.chat.requestBluetoothEnable
import com.ssafy.lanterns.ui.components.ChatMessageBubble
import com.ssafy.lanterns.ui.components.ProfileAvatar

@Composable
fun DirectChatScreen(
    navController: NavController,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // 현재 1:1 채팅 상대
    val chatPartner = uiState.currentChatPartner
    
    // 블루투스 권한 요청을 위한 launcher
    val permissionLauncher = rememberBluetoothPermissionLauncher { isGranted ->
        viewModel.updatePermissionStatus(isGranted)
        
        if (isGranted) {
            // 권한이 모두 승인되면 메시 네트워크 시작
            viewModel.startMeshNetwork()
        }
    }
    
    // 권한 확인 및 네트워크 시작
    LaunchedEffect(Unit) {
        // 권한 확인 및 요청
        if (!uiState.permissionGranted) {
            requestBluetoothPermissions(permissionLauncher)
        } else if (!uiState.isNetworkActive) {
            // 권한이 있고 네트워크가 활성화되지 않았다면 시작
            viewModel.startMeshNetwork()
        }
    }
    
    // 필터링된 메시지 목록 (현재 채팅 상대와의 메시지만)
    val filteredMessages = remember(uiState.messages, chatPartner) {
        uiState.messages.filter { message -> 
            // 상대방이 보낸 메시지이면서 내게 보낸 메시지 또는
            // 내가 보낸 메시지이면서 이 상대방에게 보낸 메시지
            (message.senderNickname != uiState.deviceNickname && message.recipient == null) ||
            (message.senderNickname == uiState.deviceNickname && message.recipient == chatPartner?.address) ||
            (message.senderNickname == chatPartner?.nickname && message.recipient == null) ||
            (message.senderNickname != uiState.deviceNickname && message.sender == chatPartner?.address)
        }
    }
    
    // 메시지 입력 상태
    var messageText by remember { mutableStateOf("") }
    
    // 현재 채팅 상대가 없으면 그룹 채팅으로 되돌아가기
    if (chatPartner == null) {
        LaunchedEffect(Unit) {
            navController.popBackStack()
        }
        return
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 앱바
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 상대방 프로필 아이콘
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = chatPartner.nickname.take(1).uppercase(),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column {
                            Text(
                                text = chatPartner.nickname,
                                style = MaterialTheme.typography.titleMedium
                            )
                            
                            // 연결 상태 표시
                            BluetoothConnectionStatus(uiState.connectionState)
                        }
                    }
                },
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.returnToGroupChat()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로 가기")
                    }
                }
            )
            
            // 블루투스 활성화 상태에 따른 안내 표시
            if (!uiState.bluetoothEnabled) {
                BluetoothDisabledWarning {
                    // 블루투스 활성화 요청
                    requestBluetoothEnable(context)
                }
            }
            
            // 채팅 메시지 목록
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp),
                reverseLayout = true
            ) {
                items(filteredMessages.reversed()) { message ->
                    DirectMessageItem(
                        message = message,
                        isMe = message.senderNickname == uiState.deviceNickname
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            // 메시지 입력
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp)
                        .border(1.dp, Color.Gray, RoundedCornerShape(24.dp))
                        .padding(12.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (messageText.isEmpty()) {
                                Text(
                                    "메시지를 입력하세요...",
                                    color = Color.Gray
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                
                // 메시지 전송 버튼
                IconButton(
                    onClick = {
                        if (messageText.isNotEmpty()) {
                            // 메시지 전송
                            viewModel.sendDirectMessage(messageText)
                            messageText = ""
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "메시지 보내기",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        // 에러 메시지
        uiState.errorMessage?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("확인")
                    }
                }
            ) {
                Text(error)
            }
        }
    }
}

@Composable
fun DirectMessageItem(message: ChatMessage, isMe: Boolean) {
    val formattedTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.8f),
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isMe) {
                // 상대방 프로필 아바타
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = message.senderNickname.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            
            Column(
                horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
            ) {
                if (!isMe) {
                    Text(
                        text = message.senderNickname,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                ) {
                    if (!isMe) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = message.content,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        Text(
                            text = formattedTime,
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    } else {
                        Text(
                            text = formattedTime,
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = message.content,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        
                        // 메시지 상태 표시
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (message.isDelivered) Icons.Default.Done else Icons.Default.Schedule,
                            contentDescription = if (message.isDelivered) "전송됨" else "전송 중",
                            modifier = Modifier.size(12.dp),
                            tint = if (message.isDelivered) Color.Green else Color.Gray
                        )
                    }
                }
            }
        }
    }
}
