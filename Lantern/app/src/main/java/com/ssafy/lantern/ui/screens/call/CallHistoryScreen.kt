package com.ssafy.lantern.ui.screens.call

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.lantern.R
import com.ssafy.lantern.data.model.FriendCallItem
import com.ssafy.lantern.ui.theme.LanternTheme

// 통화 기록 화면
@Composable
fun CallHistoryScreen(
    onBackClick: () -> Unit,
    onCallItemClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top Bar
        TopAppBar(
            title = { Text("통화 기록") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            backgroundColor = Color.Black,
            contentColor = Color.White
        )
        
        // Call History List
        val callHistory = listOf(
            FriendCallItem(
                id = 1,
                name = "김민수",
                profileImage = R.drawable.lantern_image,
                callType = "발신전화",
                timestamp = "오후 2:30",
                isRecent = true,
                phoneNumber = "010-1234-5678",
                callDuration = "1:23"
            ),
            FriendCallItem(
                id = 2,
                name = "이지은",
                profileImage = R.drawable.lantern_image,
                callType = "부재중전화",
                timestamp = "오전 11:20",
                isRecent = false,
                phoneNumber = "010-2345-6789"
            ),
            FriendCallItem(
                id = 3,
                name = "박준호",
                profileImage = R.drawable.lantern_image,
                callType = "수신전화",
                timestamp = "어제",
                isRecent = false,
                phoneNumber = "010-3456-7890",
                callDuration = "5:42"
            ),
            FriendCallItem(
                id = 4,
                name = "도경원",
                profileImage = R.drawable.lantern_image,
                callType = "발신전화",
                timestamp = "어제",
                isRecent = false,
                phoneNumber = "010-4567-8901",
                callDuration = "2:15"
            )
        )
        
        LazyColumn {
            items(callHistory) { call ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCallItemClick)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profile Image
                    Image(
                        painter = painterResource(id = call.profileImage),
                        contentDescription = "Profile Image",
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFD700))
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Call Info
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = call.name,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = when(call.callType) {
                                    "발신전화" -> painterResource(id = android.R.drawable.sym_call_outgoing)
                                    "수신전화" -> painterResource(id = android.R.drawable.sym_call_incoming)
                                    else -> painterResource(id = android.R.drawable.sym_call_missed)
                                },
                                contentDescription = "Call Type",
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(4.dp))
                            
                            Text(
                                text = "${call.callType} ${call.callDuration.takeIf { it.isNotEmpty() }?.let { "($it)" } ?: ""}",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                    
                    // Call Button
                    IconButton(
                        onClick = onCallItemClick,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFFFD700), CircleShape)
                    ) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_call),
                            contentDescription = "Call",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Divider(color = Color.DarkGray, thickness = 0.5.dp)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CallHistoryScreenPreview() {
    LanternTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            CallHistoryScreen(
                onBackClick = {},
                onCallItemClick = {}
            )
        }
    }
}