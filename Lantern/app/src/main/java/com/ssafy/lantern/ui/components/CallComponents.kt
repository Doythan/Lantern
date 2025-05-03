package com.ssafy.lantern.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.lantern.data.model.FriendCallItem
import android.R

/**
 * 통화 기록 아이템 컴포넌트
 */
@Composable
fun FriendCallItem(friend: FriendCallItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile Image
        Image(
            painter = painterResource(id = friend.profileImage),
            contentDescription = "Profile Image",
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFD700))
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Friend Info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = friend.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = when(friend.callType) {
                        "발신전화" -> painterResource(id = R.drawable.sym_call_outgoing)
                        "수신전화" -> painterResource(id = R.drawable.sym_call_incoming)
                        else -> painterResource(id = R.drawable.sym_call_missed)
                    },
                    contentDescription = "Call Type",
                    tint = Color.Gray,
                    modifier = Modifier.size(14.dp)
                )
                
                Spacer(modifier = Modifier.width(4.dp))
                
                Text(
                    text = friend.callType,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }
        
        // Timestamp
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = friend.timestamp,
                color = if (friend.isRecent) Color.White else Color.Gray,
                fontSize = 12.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Info Button
            Icon(
                painter = painterResource(id = R.drawable.ic_dialog_info),
                contentDescription = "Info",
                tint = Color(0xFF3D84FF),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 프로필 필드 컴포넌트
 */
@Composable
fun SimpleProfileField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isEditing: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Label
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        // Content Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        ) {
            // 일반 모드 (편집 불가)
            Text(
                text = value,
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isEditing) 0f else 1f)
            )
            
            // 편집 모드
            if (isEditing) {
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 16.sp
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // Divider
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (isEditing) Color(0xFFFFD700) else Color.DarkGray)
                .padding(top = 8.dp)
        )
    }
}