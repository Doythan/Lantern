package com.ssafy.lanterns.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 프로필 아바타 컴포넌트
 */
@Composable
fun ProfileAvatar(
    nickname: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (nickname.isNotEmpty()) {
            Text(
                text = nickname.first().uppercase(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "프로필",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * 메시지 상태 아이콘
 */
@Composable
fun MessageStatusIcon(status: ChatMessage.MessageStatus) {
    when (status) {
        ChatMessage.MessageStatus.SENDING -> {
            Icon(
                imageVector = Icons.Outlined.AccessTime,
                contentDescription = "전송 중",
                modifier = Modifier.size(16.dp),
                tint = Color.Gray
            )
        }
        ChatMessage.MessageStatus.DELIVERED -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "전송 완료",
                modifier = Modifier.size(16.dp),
                tint = Color.Green
            )
        }
        ChatMessage.MessageStatus.SEEN -> {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "읽음",
                modifier = Modifier.size(16.dp),
                tint = Color.Blue
            )
        }
        ChatMessage.MessageStatus.FAILED -> {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "전송 실패",
                modifier = Modifier.size(16.dp),
                tint = Color.Red
            )
        }
    }
}

/**
 * 채팅 메시지 버블 컴포넌트
 */
@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isIncoming = message.isIncoming
    val bubbleColor = when {
        message.isUrgent -> MaterialTheme.colorScheme.errorContainer
        isIncoming -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val textColor = when {
        message.isUrgent -> MaterialTheme.colorScheme.onErrorContainer
        isIncoming -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    
    val bubbleShape = when {
        isIncoming -> RoundedCornerShape(0.dp, 8.dp, 8.dp, 8.dp)
        else -> RoundedCornerShape(8.dp, 0.dp, 8.dp, 8.dp)
    }
    
    val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    val formattedTime = timeFormatter.format(Date(message.timestamp))
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isIncoming) Alignment.Start else Alignment.End
    ) {
        if (isIncoming) {
            Text(
                text = message.senderNickname,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
        
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            if (isIncoming) {
                ProfileAvatar(
                    nickname = message.senderNickname,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            
            Surface(
                color = bubbleColor,
                shape = bubbleShape,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = message.content,
                        color = textColor,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = formattedTime,
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.7f)
                        )
                        
                        if (!isIncoming) {
                            Spacer(modifier = Modifier.width(4.dp))
                            MessageStatusIcon(status = message.messageStatus)
                        }
                    }
                }
            }
            
            if (!isIncoming) {
                Spacer(modifier = Modifier.width(44.dp)) // 프로필 아바타 공간 유지
            }
        }
    }
}

/**
 * 시스템 메시지 컴포넌트
 */
@Composable
fun SystemMessageItem(message: ChatMessage) {
    Text(
        text = message.content,
        color = MaterialTheme.colorScheme.outline,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun ChatMessageBubblePreview() {
    MaterialTheme {
        Column {
            // 수신 메시지
            ChatMessageBubble(
                message = ChatMessage(
                    id = UUID.randomUUID(),
                    sender = 1,
                    senderNickname = "홍길동",
                    content = "안녕하세요! BLE 메시 네트워크로 전송된 메시지입니다.",
                    isIncoming = true,
                    isDelivered = true,
                    messageStatus = ChatMessage.MessageStatus.DELIVERED
                )
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 발신 메시지 (전송 중)
            ChatMessageBubble(
                message = ChatMessage(
                    id = UUID.randomUUID(),
                    sender = 2,
                    senderNickname = "나",
                    content = "네 반갑습니다. 저는 지금 메시지를 보내고 있어요.",
                    isIncoming = false,
                    isDelivered = false,
                    messageStatus = ChatMessage.MessageStatus.SENDING
                )
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 발신 메시지 (전송 완료)
            ChatMessageBubble(
                message = ChatMessage(
                    id = UUID.randomUUID(),
                    sender = 2,
                    senderNickname = "나",
                    content = "메시지가 성공적으로 전송되었습니다.",
                    isIncoming = false,
                    isDelivered = true,
                    messageStatus = ChatMessage.MessageStatus.DELIVERED
                )
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 긴급 메시지
            ChatMessageBubble(
                message = ChatMessage(
                    id = UUID.randomUUID(),
                    sender = 1,
                    senderNickname = "홍길동",
                    content = "이것은 긴급 메시지입니다!",
                    isIncoming = true,
                    isUrgent = true,
                    messageStatus = ChatMessage.MessageStatus.DELIVERED
                )
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 시스템 메시지
            SystemMessageItem(
                message = ChatMessage.createSystemMessage("새로운 사용자가 채팅방에 참여했습니다.")
            )
        }
    }
} 