package com.ssafy.lanterns.ui.screens.call

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ssafy.lanterns.data.model.CallHistory
import com.ssafy.lanterns.ui.theme.*
import com.ssafy.lanterns.ui.util.getProfileImageResId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 통화 기록 화면
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(
    navController: NavController,
    onCallClick: (String, String, Int) -> Unit,
    viewModel: CallViewModel = hiltViewModel()
) {
    // 통화 기록 데이터
    val callHistories by viewModel.callHistories.collectAsState()
    
    // 삭제 확인 다이얼로그 상태
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedHistoryId by remember { mutableStateOf<Long?>(null) }
    
    // 통화 기록 로드
    LaunchedEffect(Unit) {
        viewModel.loadCallHistories()
    }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "통화 기록",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (callHistories.isNotEmpty()) {
                        IconButton(onClick = {
                            showDeleteDialog = true
                            selectedHistoryId = null // 모든 기록 삭제를 의미
                        }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear All"
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (callHistories.isEmpty()) {
            // 통화 기록이 없는 경우
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "통화 기록이 없습니다",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            // 통화 기록 목록
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                items(callHistories) { history ->
                    CallHistoryItem(
                        callHistory = history,
                        onCallClick = {
                            onCallClick(
                                history.deviceAddress,
                                history.deviceName,
                                1 // 기본 프로필 ID
                            )
                        },
                        onDeleteClick = {
                            selectedHistoryId = history.id
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }
    }
    
    // 삭제 확인 다이얼로그
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("통화 기록 삭제") },
            text = { 
                Text(
                    if (selectedHistoryId == null) 
                        "모든 통화 기록을 삭제하시겠습니까?" 
                    else 
                        "이 통화 기록을 삭제하시겠습니까?"
                ) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (selectedHistoryId == null) {
                            viewModel.clearAllCallHistories()
                        } else {
                            viewModel.deleteCallHistory(selectedHistoryId!!)
                        }
                        showDeleteDialog = false
                    }
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

/**
 * 통화 기록 항목
 */
@Composable
fun CallHistoryItem(
    callHistory: CallHistory,
    onCallClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    // 날짜 및 시간 포맷
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val timeString = dateFormat.format(callHistory.timestamp)
    
    // 통화 시간 포맷 (분:초)
    val durationString = if (callHistory.duration > 0) {
        val minutes = callHistory.duration / 60
        val seconds = callHistory.duration % 60
        "${minutes}:${seconds.toString().padStart(2, '0')}"
    } else {
        "미응답"
    }
    
    // 통화 타입 아이콘 및 색상
    val (icon, tint) = when {
        callHistory.isOutgoing && callHistory.isAnswered -> 
            Icons.Default.CallMade to MaterialTheme.colorScheme.primary
        callHistory.isOutgoing && !callHistory.isAnswered -> 
            Icons.Default.CallMissedOutgoing to MaterialTheme.colorScheme.error
        !callHistory.isOutgoing && callHistory.isAnswered -> 
            Icons.Default.CallReceived to MaterialTheme.colorScheme.primary
        else -> 
            Icons.Default.CallMissed to MaterialTheme.colorScheme.error
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 프로필 이미지 (임의로 생성)
            val profileId = callHistory.deviceAddress.hashCode() % 8 + 1
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = getProfileImageResId(profileId)),
                    contentDescription = "Profile",
                    modifier = Modifier.size(40.dp)
                )
            }
            
            // 통화 정보
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(18.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = callHistory.deviceName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Text(
                    text = "$timeString ($durationString)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            // 작업 버튼들
            Row {
                // 통화 버튼
                IconButton(onClick = onCallClick) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                // 삭제 버튼
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CallHistoryScreenPreview() {
    LanternsTheme {
        // 임시 미리보기 데이터
        val previewHistories = listOf(
            CallHistory(
                id = 1,
                deviceAddress = "00:11:22:33:44:55",
                deviceName = "김민수",
                timestamp = Date(),
                duration = 65, // 1분 5초
                isOutgoing = true,
                isAnswered = true
            ),
            CallHistory(
                id = 2,
                deviceAddress = "55:44:33:22:11:00",
                deviceName = "이지연",
                timestamp = Date(System.currentTimeMillis() - 3600000), // 1시간 전
                duration = 0,
                isOutgoing = false,
                isAnswered = false
            )
        )
        
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                items(previewHistories) { history ->
                    CallHistoryItem(
                        callHistory = history,
                        onCallClick = {},
                        onDeleteClick = {}
                    )
                }
            }
        }
    }
} 