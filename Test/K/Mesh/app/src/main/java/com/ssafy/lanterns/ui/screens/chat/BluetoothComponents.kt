package com.ssafy.lanterns.ui.screens.chat

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import android.bluetooth.BluetoothAdapter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.ssafy.lanterns.service.ConnectionState
import com.ssafy.lanterns.ui.screens.chat.ChatViewModel

/**
 * 블루투스 비활성화 경고 UI 컴포넌트
 */
@Composable
fun BluetoothDisabledWarning(onEnableRequest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFD700))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = "경고",
                tint = Color.Black
            )
            Text(
                "블루투스가 비활성화되어 있습니다.",
                color = Color.Black,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onEnableRequest,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
            ) {
                Text("활성화", color = Color.White)
            }
        }
    }
}

/**
 * 컴포즈에서 사용할 블루투스 권한 요청 Launcher를 생성
 */
@Composable
fun rememberBluetoothPermissionLauncher(
    onPermissionResult: (Boolean) -> Unit
): ActivityResultLauncher<Array<String>> {
    val context = LocalContext.current
    
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        onPermissionResult(allGranted)
        
        if (!allGranted) {
            Toast.makeText(context, "블루투스 권한이 필요합니다", Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * 필요한 블루투스 권한 요청
 */
fun requestBluetoothPermissions(
    permissionLauncher: ActivityResultLauncher<Array<String>>
) {
    // Android 12 (API 31) 이상에서 필요한 권한
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        // Android 12 미만에서 필요한 권한
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    
    // 권한 요청
    permissionLauncher.launch(permissions)
}

/**
 * 블루투스 활성화 요청 함수
 */
fun requestBluetoothEnable(context: android.content.Context) {
    try {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        context.startActivity(enableBtIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "블루투스를 활성화할 수 없습니다", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 향상된 블루투스 권한 요청 화면
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BluetoothPermissionScreen(
    viewModel: ChatViewModel,
    onPermissionGranted: () -> Unit
) {
    val requiredPermissions = viewModel.getRequiredPermissions()
    val permissionsState = rememberMultiplePermissionsState(permissions = requiredPermissions)
    val context = LocalContext.current
    
    // 권한 거부 횟수 추적
    var permissionDeniedCount by remember { mutableStateOf(0) }
    var showRationale by remember { mutableStateOf(false) }
    
    // 블루투스 권한 요청 결과 처리
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            onPermissionGranted()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = "블루투스",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                
                Text(
                    text = "블루투스 권한이 필요합니다",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "주변 기기와 통신하기 위해 블루투스 권한이 필요합니다. 권한을 허용하면 인터넷 없이도 메시지를 주고받을 수 있습니다.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 필요한 권한 표시
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    requiredPermissions.forEach { permission ->
                        val permissionName = when (permission) {
                            Manifest.permission.BLUETOOTH_CONNECT -> "블루투스 연결"
                            Manifest.permission.BLUETOOTH_SCAN -> "블루투스 검색"
                            Manifest.permission.BLUETOOTH_ADVERTISE -> "블루투스 광고"
                            Manifest.permission.BLUETOOTH -> "블루투스 사용"
                            Manifest.permission.BLUETOOTH_ADMIN -> "블루투스 관리"
                            Manifest.permission.ACCESS_FINE_LOCATION -> "위치 정보 접근"
                            else -> permission
                        }
                        
                        val isGranted = permissionsState.permissions.find { it.permission == permission }?.status == PermissionStatus.Granted
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = null,
                                tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = permissionName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 권한 요청 버튼
                Button(
                    onClick = { 
                        if (permissionDeniedCount >= 2) {
                            // 여러 번 거부했을 경우, 설정 화면으로 안내
                            showRationale = true
                        } else {
                            permissionsState.launchMultiplePermissionRequest()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("권한 허용하기")
                }
                
                // 설정으로 이동 버튼
                if (permissionDeniedCount > 0) {
                    OutlinedButton(
                        onClick = { viewModel.openAppSettings() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("설정에서 권한 허용하기")
                    }
                }
            }
        }
        
        // 권한 설명 다이얼로그
        if (showRationale) {
            PermissionRationaleDialog(
                onDismiss = { showRationale = false },
                onGoToSettings = {
                    showRationale = false
                    viewModel.openAppSettings()
                }
            )
        }
    }
    
    // 권한 거부 시 카운트 증가
    LaunchedEffect(permissionsState.permissions.map { it.status }) {
        val deniedCount = permissionsState.permissions.count { it.status is PermissionStatus.Denied }
        if (deniedCount > 0 && permissionsState.permissions.any { it.status is PermissionStatus.Denied }) {
            permissionDeniedCount++
        }
    }
}

/**
 * 권한 거부 시 표시되는 다이얼로그
 */
@Composable
fun PermissionRationaleDialog(
    onDismiss: () -> Unit,
    onGoToSettings: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "권한이 필요합니다",
                    style = MaterialTheme.typography.titleLarge
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "블루투스 메시 네트워크를 사용하려면 권한이 필요합니다. 설정에서 권한을 허용해 주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("취소")
                    }
                    
                    Button(onClick = onGoToSettings) {
                        Text("설정으로 이동")
                    }
                }
            }
        }
    }
}

/**
 * 블루투스 연결 상태 표시 UI
 */
@Composable
fun BluetoothConnectionStatus(connectionState: ConnectionState) {
    val (icon, text, color) = when (connectionState) {
        ConnectionState.CONNECTED -> Triple(
            Icons.Default.Bluetooth,
            "블루투스 연결됨",
            MaterialTheme.colorScheme.primary
        )
        ConnectionState.CONNECTING -> Triple(
            Icons.Default.Bluetooth,
            "연결 중...",
            MaterialTheme.colorScheme.primary
        )
        ConnectionState.DISCONNECTED -> Triple(
            Icons.Default.BluetoothDisabled,
            "연결 끊김",
            MaterialTheme.colorScheme.error
        )
        else -> Triple(
            Icons.Default.BluetoothDisabled,
            "연결 상태 확인 중...",
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.1f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        if (connectionState == ConnectionState.CONNECTING) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = color
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(4.dp))
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
} 