package com.ssafy.lanterns.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ssafy.lanterns.ui.components.ChatMessageBubble
import com.ssafy.lanterns.ui.components.ProfileAvatar
import com.ssafy.lanterns.ui.navigation.AppDestinations
import com.ssafy.lanterns.ui.theme.LanternTheme
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import android.bluetooth.BluetoothDevice
import android.widget.Toast
import com.ssafy.lanterns.data.model.User
import com.ssafy.lanterns.data.repository.UserRepository
import android.bluetooth.BluetoothAdapter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import com.ssafy.lanterns.data.source.ble.mesh.ChatMessage
import com.ssafy.lanterns.data.source.ble.mesh.NearbyNode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import com.ssafy.lanterns.service.ConnectionState
import com.ssafy.lanterns.ui.screens.chat.BluetoothDisabledWarning
import com.ssafy.lanterns.ui.screens.chat.rememberBluetoothPermissionLauncher
import com.ssafy.lanterns.ui.screens.chat.requestBluetoothPermissions
import com.ssafy.lanterns.ui.screens.chat.requestBluetoothEnable

// 더미 메시지 데이터 모델
data class Message(
    val id: Int,
    val sender: String,
    val text: String,
    val time: String,
    val isMe: Boolean = false,
    val senderProfileId: Int? = null
)

// 스캔된 기기 아이템 모델
data class ScanDeviceItem(
    val device: BluetoothDevice,
    val nickname: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicChatScreen(
    navController: NavController,
    viewModel: ChatViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val localContext = LocalContext.current
    
    // 메시지 입력 상태
    var messageText by remember { mutableStateOf("") }
    
    // 처음 접속 시 닉네임 설정 및 권한 요청
    LaunchedEffect(Unit) {
        if (uiState.deviceNickname.isEmpty() || uiState.deviceNickname == "Unknown") {
            viewModel.showNicknameDialog(true)
        }
        
        // 블루투스 상태 확인
        viewModel.updateBluetoothState()
    }
    
    // 권한 요청 화면 표시
    if (!uiState.permissionGranted) {
        BluetoothPermissionScreen(
            viewModel = viewModel,
            onPermissionGranted = {
                // 권한이 모두 승인되면 메시 네트워크 시작
                viewModel.updatePermissionStatus(true)
                viewModel.startMeshNetwork()
            }
        )
        return
    }
    
    // 블루투스 활성화 상태에 따른 안내 표시
    if (!uiState.bluetoothEnabled) {
        BluetoothDisabledWarning {
            // 블루투스 활성화 요청
            viewModel.openBluetoothSettings()
        }
        return
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 앱바
            TopAppBar(
                title = { Text("공용 채팅방") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    // 연결 상태 표시
                    BluetoothConnectionStatus(uiState.connectionState)
                    
                    IconButton(onClick = {
                        // 권한이 있는지 확인 후 스캔 시작
                        if (uiState.permissionGranted) {
                            viewModel.startMeshNetwork()
                        } else {
                            viewModel.updatePermissionStatus(false)
                        }
                    }) {
                        Icon(
                            imageVector = if (uiState.isNetworkActive) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled,
                            contentDescription = "블루투스 연결 상태",
                            tint = if (uiState.isNetworkActive) Color.Green else Color.Red
                        )
                    }
                    
                    IconButton(onClick = {
                        viewModel.showNicknameDialog(true)
                    }) {
                        Icon(Icons.Default.Person, contentDescription = "닉네임 설정")
                    }
                }
            )
            
            // 주변 사용자 목록
            if (uiState.nearbyDevices.isNotEmpty()) {
                Text(
                    "주변 사용자 (${uiState.nearbyDevices.size}명)",
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                    fontWeight = FontWeight.Bold
                )
                
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.nearbyDevices) { device ->
                        NearbyDeviceItem(device) {
                            // 1:1 채팅 화면으로 이동
                            viewModel.startDirectChat(device)
                            navController.navigate("direct_chat")
                        }
                    }
                }
                
                HorizontalDivider()
            }
            
            // 메시지 목록
            val messages = remember(uiState.messages) { uiState.messages.reversed() }
            
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp),
                reverseLayout = true
            ) {
                items(messages) { message ->
                    MessageItem(
                        message = message,
                        isMe = message.senderNickname == uiState.deviceNickname
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            // 메시지 입력
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background
            ) {
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
                    
                    IconButton(
                        onClick = {
                            if (messageText.isNotEmpty()) {
                                viewModel.sendGroupMessage(messageText)
                                messageText = ""
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "전송",
                            tint = if (messageText.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                }
            }
        }
        
        // 닉네임 설정 다이얼로그
        if (uiState.showNicknameDialog) {
            NicknameDialog(
                initialNickname = uiState.deviceNickname,
                onDismiss = { viewModel.showNicknameDialog(false) },
                onConfirm = { nickname ->
                    if (nickname.isNotEmpty()) {
                        viewModel.updateDeviceNickname(nickname)
                    }
                    viewModel.showNicknameDialog(false)
                }
            )
        }
        
        // 에러 메시지 표시
        uiState.errorMessage?.let { errorMessage ->
            LaunchedEffect(errorMessage) {
                Toast.makeText(localContext, errorMessage, Toast.LENGTH_SHORT).show()
                // 에러 메시지 표시 후 지우기
                viewModel.clearError()
            }
        }
    }
}

@Composable
fun NearbyDeviceItem(device: NearbyNode, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = device.nickname.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Text(
            text = device.nickname,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 1
        )
    }
}

@Composable
fun MessageItem(message: ChatMessage, isMe: Boolean) {
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

@Composable
fun NicknameDialog(
    initialNickname: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var nickname by remember { mutableStateOf(initialNickname) }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "닉네임 설정",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("닉네임") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("취소")
                    }
                    
                    TextButton(
                        onClick = {
                            if (nickname.isNotEmpty()) {
                                onConfirm(nickname)
                            }
                        }
                    ) {
                        Text("확인")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PublicChatScreenPreview() {
    LanternTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val dummyNavController = rememberNavController()
            val dummyViewModel: ChatViewModel = hiltViewModel()
            PublicChatScreen(navController = dummyNavController, viewModel = dummyViewModel)
        }
    }
}
