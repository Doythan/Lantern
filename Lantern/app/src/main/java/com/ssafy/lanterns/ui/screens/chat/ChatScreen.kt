package com.ssafy.lanterns.ui.screens.chat

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.* // Material Components 임포트
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.permissions.* // Accompanist Permissions 임포트
import com.ssafy.lanterns.ui.theme.LanternTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class) // Accompanist Permissions 사용
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val messagesListState = rememberLazyListState()
    val scannedDevicesListState = rememberLazyListState()
    var textState by remember { mutableStateOf("") }

    // --- 권한 처리 --- //
    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION // 스캔에 필요
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions = requiredPermissions) { permissionsResultMap ->
        viewModel.updatePermissionStatus(permissionsResultMap.values.all { it })
    }

    // 블루투스 활성화 요청 Launcher
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.updateBluetoothState(result.resultCode == android.app.Activity.RESULT_OK)
    }

    // --- 생명주기 및 초기화 --- //
    // 권한 상태 최초 확인
    LaunchedEffect(Unit) {
        viewModel.updatePermissionStatus(permissionState.permissions.all { it.status.isGranted })
    }

    LaunchedEffect(key1 = uiState.requiredPermissionsGranted, key2 = uiState.isBluetoothEnabled) {
        if (uiState.requiredPermissionsGranted && uiState.isBluetoothEnabled) {
            viewModel.startBleOperations()
        } else {
            viewModel.stopBleOperations()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (uiState.requiredPermissionsGranted && uiState.isBluetoothEnabled) {
                         viewModel.startBleOperations() // 화면 돌아왔을 때 재시작
                     }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.stopBleOperations()
                }
                else -> { /* Do nothing */ }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // --- UI --- //
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("랜턴 채팅") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // 권한 및 블루투스 상태
            PermissionAndBluetoothStatus(permissionState, uiState.isBluetoothEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBluetoothLauncher.launch(enableBtIntent)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 스캔된 기기 목록 및 연결 버튼
            Text("스캔된 기기:", fontWeight = FontWeight.Bold)
            LazyColumn(modifier = Modifier.height(100.dp).fillMaxWidth(), state = scannedDevicesListState) {
                 items(uiState.scannedDevices.toList()) { (address, name) ->
                     Row(
                         modifier = Modifier.fillMaxWidth().clickable { viewModel.connectToDevice(address) }.padding(vertical = 4.dp),
                         horizontalArrangement = Arrangement.SpaceBetween
                     ) {
                         Text("$name ($address)")
                         // 연결 버튼 (옵션)
                         // Button(onClick = { viewModel.connectToDevice(address) }, enabled = uiState.connectionState == BluetoothProfile.STATE_DISCONNECTED) {
                         //     Text("연결")
                         // }
                     }
                     Divider()
                 }
            }

             Spacer(modifier = Modifier.height(8.dp))

             // 연결 상태 표시
            ConnectionStatus(uiState = uiState)

             Spacer(modifier = Modifier.height(8.dp))

            // 채팅 로그
            Text("채팅 로그:", fontWeight = FontWeight.Bold)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = messagesListState,
                reverseLayout = true
            ) {
                items(uiState.messages.reversed()) { message ->
                    ChatMessageItem(message)
                }
            }
            // 메시지 수신 시 스크롤
            LaunchedEffect(uiState.messages.size) {
                if (uiState.messages.isNotEmpty()) {
                    coroutineScope.launch {
                        messagesListState.animateScrollToItem(0)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 메시지 입력 및 전송
            MessageInput(textState, { textState = it }, uiState) {
                 viewModel.sendMessage(textState)
                 textState = ""
            }

             // 에러 메시지 표시
            uiState.errorMessage?.let {
                LaunchedEffect(it) {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    // 에러 메시지 표시 후 ViewModel에서 초기화 필요
                }
            }
        }
    }
}

// 권한 및 블루투스 상태 UI
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionAndBluetoothStatus(
    permissionState: MultiplePermissionsState,
    isBluetoothEnabled: Boolean,
    onRequestBluetoothEnable: () -> Unit
) {
    Column {
        if (!permissionState.permissions.all { it.status.isGranted }) {
            Text("채팅 기능을 사용하려면 블루투스 및 위치 권한이 필요합니다.")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
                Text("권한 요청")
            }
        } else if (!isBluetoothEnabled) {
            Text("채팅 기능을 사용하려면 블루투스를 활성화해야 합니다.")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRequestBluetoothEnable) {
                Text("블루투스 켜기")
            }
        }
    }
}

// 연결 상태 표시 UI
@Composable
private fun ConnectionStatus(uiState: ChatUiState) {
    val statusText = when (uiState.connectionState) {
        BluetoothProfile.STATE_CONNECTED -> "연결됨: ${uiState.connectedDevice?.name ?: uiState.connectedDevice?.address}"
        BluetoothProfile.STATE_CONNECTING -> "연결 중..."
        BluetoothProfile.STATE_DISCONNECTED -> "연결되지 않음"
        BluetoothProfile.STATE_DISCONNECTING -> "연결 해제 중..."
        else -> "알 수 없음"
    }
    Text(statusText, style = MaterialTheme.typography.subtitle1)
}

// 채팅 메시지 아이템 UI
@Composable
private fun ChatMessageItem(message: ChatMessage) {
    // 메시지 보낸 사람에 따라 정렬 등 스타일 변경 가능
    val alignment = if (message.sender == "나") Alignment.CenterEnd else Alignment.CenterStart
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = "${message.sender}: ${message.text}",
            modifier = Modifier.align(alignment).padding(horizontal = 8.dp)
            // TODO: 말풍선 배경 등 추가
        )
    }
}

// 메시지 입력 UI
@Composable
private fun MessageInput(
    textState: String,
    onTextChange: (String) -> Unit,
    uiState: ChatUiState,
    onSendClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = textState,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("메시지를 입력하세요") },
            enabled = uiState.connectionState == BluetoothProfile.STATE_CONNECTED
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onSendClick,
            enabled = textState.isNotBlank() && uiState.connectionState == BluetoothProfile.STATE_CONNECTED
        ) {
            Text("전송")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    LanternTheme {
        ChatScreen()
    }
} 