package com.ssafy.lanterns.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ssafy.lanterns.ui.components.ProfileAvatar
import com.ssafy.lanterns.ui.navigation.AppDestinations
import com.ssafy.lanterns.ui.theme.*
import com.ssafy.lanterns.utils.getConnectionColorByDistance
import com.ssafy.lanterns.utils.getConnectionStrengthText
import kotlin.math.roundToInt

// 이징 애니메이션 커브 (모달 애니메이션용)
private val EaseOutQuart = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)
private val EaseInQuad = CubicBezierEasing(0.55f, 0.085f, 0.68f, 0.53f)

/**
 * 사용자 정보 데이터 클래스
 */
data class ChatUser(
    val id: Int,
    val name: String,
    val distance: Float, // 거리 (미터 단위)
    val messageCount: Float // 메시지 개수 또는 다른 정보
)

/**
 * 주변 사용자 목록 모달
 */
@Composable
fun NearbyUsersModal(
    users: List<ChatUser>,
    onDismiss: () -> Unit,
    navController: NavController? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // 배경 어둡게 처리 (탭해서 닫기 가능)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.5f)
                    .background(color = androidx.compose.ui.graphics.Color.Black)
                    .clickable { onDismiss() }
            )
            
            // 모달 콘텐츠 - 아래에서 위로 슬라이드 애니메이션
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300, easing = EaseOutQuart)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(200, easing = EaseInQuad)
                )
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = NavyTop
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 8.dp
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // 헤더 영역
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "채팅방 참여자 (${users.size})",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "닫기",
                                    tint = TextWhite70
                                )
                            }
                        }
                        
                        HorizontalDivider(color = TextWhite.copy(alpha = 0.1f))
                        
                        // 사용자 목록
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(users) { user ->
                                ChatUserItem(
                                    user = user,
                                    navController = navController,
                                    onDismiss = onDismiss
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 채팅 사용자 목록 아이템
 */
@Composable
fun ChatUserItem(
    user: ChatUser,
    navController: NavController? = null,
    onDismiss: (() -> Unit)? = null
) {
    val connectionColor = getConnectionColorByDistance(user.distance)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = navController != null) {
                // 프로필 화면으로 이동
                navController?.let { 
                    val route = AppDestinations.PROFILE_ROUTE
                        .replace("{userId}", user.id.toString())
                        .replace("{name}", user.name)
                        .replace("{distance}", "${user.distance.toInt()}m")
                    it.navigate(route)
                    onDismiss?.invoke() // 모달 닫기
                }
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = NavyBottom
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 사용자 아바타
            ProfileAvatar(
                profileId = user.id,
                borderColor = connectionColor,
                hasBorder = true,
                size = 48.dp,
                onClick = if (navController != null) {
                    {
                        // 프로필 화면으로 이동
                        val route = AppDestinations.PROFILE_ROUTE
                            .replace("{userId}", user.id.toString())
                            .replace("{name}", user.name)
                            .replace("{distance}", "${user.distance.toInt()}m")
                        navController.navigate(route)
                        onDismiss?.invoke() // 모달 닫기
                    }
                } else null
            )
            
            // 사용자 정보
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "거리:",
                        fontSize = 12.sp,
                        color = TextWhite70
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${user.distance.toInt()}m",
                        fontSize = 12.sp,
                        color = connectionColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ConnectionStrengthIndicator(
                        distance = user.distance,
                        color = connectionColor
                    )
                }
            }
            
            // 거리 표시
            Box(
                modifier = Modifier
                    .background(
                        color = connectionColor.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = getConnectionStrengthText(user.distance),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = connectionColor
                )
            }
        }
    }
}

/**
 * 연결 강도 표시 컴포넌트
 */
@Composable
fun ConnectionStrengthIndicator(
    distance: Float,
    color: Color
) {
    // 거리에 따라 바의 개수 결정 (최대 5개, 최소 1개)
    val bars = when {
        distance <= 100f -> 5 // 0-100m: 5개 바
        distance <= 150f -> 4 // 100-150m: 4개 바
        distance <= 200f -> 3 // 150-200m: 3개 바
        distance <= 300f -> 2 // 200-300m: 2개 바
        else -> 1 // 300m 이상: 1개 바
    }
    
    Row(
        modifier = Modifier.width(60.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { index ->
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .weight(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        color = if (index < bars) color else TextWhite.copy(alpha = 0.2f)
                    )
            )
        }
    }
}

@Preview
@Composable
fun NearbyUsersModalPreview() {
    LanternTheme {
        val dummyNavController = rememberNavController()
        val dummyUsers = listOf(
            ChatUser(1, "도경원", 50f, 5f),
            ChatUser(2, "유저2", 150f, 3f),
            ChatUser(3, "유저3", 300f, 1f)
        )
        NearbyUsersModal(
            users = dummyUsers, 
            onDismiss = {},
            navController = dummyNavController
        )
    }
} 