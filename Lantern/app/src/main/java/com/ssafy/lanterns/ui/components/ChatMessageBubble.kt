package com.ssafy.lanterns.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ssafy.lanterns.ui.navigation.AppDestinations
import com.ssafy.lanterns.ui.theme.*
import com.ssafy.lanterns.ui.util.getProfileImageResId

/**
 * 채팅 메시지 버블 컴포넌트
 *
 * @param senderName 발신자 이름
 * @param text 메시지 내용
 * @param time 전송 시간
 * @param isMe 내가 보낸 메시지인지 여부
 * @param senderProfileId 발신자 프로필 ID (색상 지정용)
 * @param navController 선택사항: 프로필 클릭 시 프로필 화면으로 이동하기 위한 네비게이션 컨트롤러
 * @param distance 선택사항: 거리 정보 (미터 단위)
 */
@Composable
fun ChatMessageBubble(
    senderName: String,
    text: String,
    time: String,
    isMe: Boolean = false,
    senderProfileId: Int? = null,
    navController: NavController? = null,
    distance: Float = 50f
) {
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isMe) 16.dp else 4.dp,
        bottomEnd = if (isMe) 4.dp else 16.dp
    )

    // 기본 연결 색상 사용
    val connectionColor = BleBlue1
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMe) {
            // 프로필 아바타 (다른 사람의 메시지일 때만 표시)
            Box(
                modifier = Modifier.clip(CircleShape)
            ) {
                ProfileAvatar(
                    profileId = senderProfileId ?: 1,
                    size = 36.dp,
                    borderColor = connectionColor,
                    hasBorder = true,
                    onClick = if (navController != null && senderProfileId != null) {
                        {
                            // 프로필 화면으로 이동
                            val route = AppDestinations.PROFILE_ROUTE
                                .replace("{userId}", senderProfileId.toString())
                                .replace("{name}", senderName)
                                .replace("{distance}", "${distance.toInt()}m")
                            navController.navigate(route)
                        }
                    } else null
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            if (!isMe) {
                Text(
                    text = senderName,
                    fontSize = 12.sp,
                    color = TextWhite70,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }
            
            Column(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(if (isMe) ChatBubbleMine else ChatBubbleOthers)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = text,
                    color = TextWhite,
                    fontSize = 16.sp
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = time,
                    fontSize = 10.sp,
                    color = TextWhite70,
                    modifier = Modifier.align(if (isMe) Alignment.Start else Alignment.End)
                )
            }
        }
        
        if (isMe) {
            Spacer(modifier = Modifier.width(8.dp))
            // 프로필 아바타 (내 메시지일 때는 더 작게 표시)
            Box(
                modifier = Modifier.clip(CircleShape)
            ) {
                ProfileAvatar(
                    profileId = senderProfileId ?: 1,
                    size = 24.dp,
                    borderColor = connectionColor,
                    hasBorder = true,
                    onClick = if (navController != null && senderProfileId != null) {
                        {
                            // 프로필 화면으로 이동
                            val route = AppDestinations.PROFILE_ROUTE
                                .replace("{userId}", senderProfileId.toString())
                                .replace("{name}", senderName)
                                .replace("{distance}", "${distance.toInt()}m")
                            navController.navigate(route)
                        }
                    } else null
                )
            }
        }
    }
}

/**
 * 사용자 프로필 아바타 컴포넌트 - 채팅에서 사용하는 버전
 * 
 * @deprecated 기존 코드와의 호환성을 위해 유지. ProfileAvatar를 대신 사용하세요.
 */
@Composable
@Deprecated("ProfileAvatar를 사용하세요", ReplaceWith("ProfileAvatar(profileId, size, modifier, connectionColor, true, onClick)"))
fun ChatProfileAvatar(
    profileId: Int,
    connectionColor: Color = ConnectionNear,
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null
) {
    ProfileAvatar(
        profileId = profileId,
        size = size,
        borderColor = connectionColor,
        hasBorder = true,
        onClick = onClick
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF051225)
@Composable
fun ChatMessageBubblePreview() {
    Column {
        val dummyNavController = rememberNavController()
        
        ChatMessageBubble(
            senderName = "도경원",
            text = "안녕하세요. 채팅 테스트입니다.",
            time = "10:21 PM",
            isMe = true,
            senderProfileId = 1,
            navController = dummyNavController
        )
        
        ChatMessageBubble(
            senderName = "유저1",
            text = "반갑습니다! 이것은 다른 사람이 보낸 메시지입니다.",
            time = "10:22 PM",
            isMe = false,
            senderProfileId = 2,
            navController = dummyNavController
        )
        
        ChatMessageBubble(
            senderName = "유저2",
            text = "다른 사용자의 메시지입니다.",
            time = "10:23 PM",
            isMe = false,
            senderProfileId = 3,
            navController = dummyNavController
        )
    }
} 