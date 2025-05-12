package com.ssafy.lanterns.ui.screens.chat

// import androidx.annotation.DrawableRes // Removed, moved to ImageUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.ssafy.lanterns.R
import com.ssafy.lanterns.ui.components.ProfileAvatar
import com.ssafy.lanterns.ui.navigation.AppDestinations
import com.ssafy.lanterns.ui.theme.LanternTheme
import com.ssafy.lanterns.ui.theme.NavyTop
import com.ssafy.lanterns.ui.theme.NavyBottom
import com.ssafy.lanterns.ui.theme.TextWhite
import com.ssafy.lanterns.ui.theme.TextWhite70
import com.ssafy.lanterns.ui.theme.BleBlue1
import com.ssafy.lanterns.ui.theme.BleBlue2
import com.ssafy.lanterns.ui.theme.ConnectionNear
import com.ssafy.lanterns.ui.theme.ConnectionMedium
import com.ssafy.lanterns.ui.theme.ConnectionFar
import com.ssafy.lanterns.utils.getConnectionColorByDistance

// Data model for Chat List item
data class ChatItem(
    val id: Int,
    val name: String,
    val lastMessage: String,
    val time: String,
    val unread: Boolean = false,
    val distance: Float = 0f // 거리 정보 재추가 (미터 단위)
)

// Data model for Nearby section item (simplified)
data class NearbyUser(
    val id: Int, // 프로필 이미지용 ID
    val name: String = "사용자 $id",
    val distance: Float = 0f // 거리 정보 재추가 (미터 단위)
)

// Removed the local getProfileImageResId function, it's now in ImageUtils.kt

@Composable
fun ChatListScreen(
    paddingValues: PaddingValues = PaddingValues(),
    navController: NavController,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    // ViewModel에서 UI 상태 가져오기
    val uiState by viewModel.uiState.collectAsState()
    
    // UI 컴포넌트 렌더링
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(NavyTop, NavyBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(NavyTop, NavyBottom)
                    )
                )
                .padding(paddingValues) // Apply padding from Scaffold
        ) {
            // Nearby Section
            NearbySection(nearbyUsers = uiState.nearbyUsers, navController = navController)

            Spacer(modifier = Modifier.height(24.dp)) // Increased space between Nearby and Chat sections

            // Chat Section Label
            Text(
                text = "채팅",
                color = TextWhite,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 16.dp, bottom = 16.dp) // Style matches "주변"
            )

            // Chat List (including Public Chat at the top)
            LazyColumn(
                modifier = Modifier.weight(1f) // Takes remaining space
            ) {
                // Public Chat Item - Always at the top
                item {
                    PublicChatListItem(navController = navController)
                    HorizontalDivider(
                        color = TextWhite.copy(alpha = 0.12f),
                        thickness = 1.dp,
                        // Indent divider like other items, starting after the icon area
                        modifier = Modifier.padding(start = 76.dp, end = 16.dp)
                    )
                }

                // Private Chat List Items
                items(uiState.chatList) { chat ->
                    ChatListItem(chat = chat, navController = navController)
                    HorizontalDivider(
                        color = TextWhite.copy(alpha = 0.12f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(start = 76.dp, end = 16.dp) // Indent divider
                    )
                }
            }
        }
    }
}

@Composable
fun NearbySection(nearbyUsers: List<NearbyUser>, navController: NavController) {
    // Section Text
    Text(
        text = "주변",
        color = TextWhite,
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
    )
    
    // Horizontal scrolling row for nearby users
    if (nearbyUsers.isNotEmpty()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(nearbyUsers) { user ->
                NearbyUserItem(user = user, navController = navController)
            }
        }
    } else {
        // 주변 사용자가 없는 경우
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "주변에 사용자가 없습니다",
                color = TextWhite70,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun NearbyUserItem(user: NearbyUser, navController: NavController) {
    val connectionColor = getConnectionColorByDistance(user.distance)
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp)
    ) {
        // Profile Image with Connection Status
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable {
                    // 프로필 화면으로 이동
                    val route = AppDestinations.PROFILE_ROUTE
                        .replace("{userId}", user.id.toString())
                        .replace("{name}", user.name)
                        .replace("{distance}", "${user.distance.toInt()}m")
                    navController.navigate(route)
                }
        ) {
            ProfileAvatar(
                profileId = user.id,
                borderColor = connectionColor,
                hasBorder = true,
                size = 64.dp
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Name
        Text(
            text = user.name,
            color = TextWhite,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
        
        // Distance
        Text(
            text = "${user.distance.toInt()}m",
            color = connectionColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PublicChatListItem(navController: NavController) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { navController.navigate(AppDestinations.PUBLIC_CHAT_ROUTE) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // White circular background Box
        Box(
            modifier = Modifier
                .size(48.dp) // Same size as profile images
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(BleBlue1, BleBlue2)
                    )
                ),
            contentAlignment = Alignment.Center // Center the image inside
        ) {
            // Image for Public Chat (Megaphone)
            Image(
                painter = painterResource(id = R.drawable.public_1),
                contentDescription = "Public Chat Icon",
                // Adjust size or padding as needed for the megaphone image within the circle
                modifier = Modifier
                    .size(32.dp) // Make image smaller than the 48dp circle
            )
        }

        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "공개 대화방",
                color = TextWhite,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "주변의 모든 사람들과 대화해보세요",
                color = TextWhite70,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ChatListItem(chat: ChatItem, navController: NavController) {
    val connectionColor = getConnectionColorByDistance(chat.distance)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { 
                // 1대1 채팅방으로 이동
                val route = AppDestinations.DIRECT_CHAT_ROUTE.replace("{userId}", chat.id.toString())
                
                // 디버깅 메시지
                println("Navigating to chat with user ID: ${chat.id}, route: $route")
                
                navController.navigate(route)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile Avatar with clickable to open profile screen
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable {
                    // 클릭 이벤트가 상위 Row에 전파되지 않도록 stopPropagation 처리
                    // 프로필 화면으로 이동
                    val route = AppDestinations.PROFILE_ROUTE
                        .replace("{userId}", chat.id.toString())
                        .replace("{name}", chat.name)
                        .replace("{distance}", "${chat.distance.toInt()}m")
                    navController.navigate(route)
                }
        ) {
            ProfileAvatar(
                profileId = chat.id,
                size = 48.dp,
                hasBorder = true,
                borderColor = connectionColor
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 채팅 정보
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // 이름과 시간
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.name,
                    color = TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = chat.time,
                    color = TextWhite70,
                    fontSize = 12.sp
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // 마지막 메시지와 거리
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.lastMessage,
                    color = TextWhite70,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                // 거리 표시
                Text(
                    text = "${chat.distance.toInt()}m",
                    color = connectionColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatListItemPreview() {
    LanternTheme {
        Surface(
            color = NavyTop
        ) {
            val dummyNavController = NavController(LocalContext.current)
            ChatListItem(
                chat = ChatItem(
                    id = 1,
                    name = "도경원",
                    lastMessage = "안녕하세요, 반갑습니다!",
                    time = "오전 10:30",
                    unread = true,
                    distance = 50f
                ),
                navController = dummyNavController
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NearbyUserItemPreview() {
    LanternTheme {
        Surface(
            color = NavyTop
        ) {
            val dummyNavController = NavController(LocalContext.current)
            NearbyUserItem(
                user = NearbyUser(
                    id = 1,
                    name = "도경원",
                    distance = 50f
                ),
                navController = dummyNavController
            )
        }
    }
}
