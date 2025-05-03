package com.ssafy.lantern.ui.screens.call

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.lantern.R
import com.ssafy.lantern.data.model.FriendCallItem
import com.ssafy.lantern.ui.components.FriendCallItem
import com.ssafy.lantern.ui.theme.LanternTheme

@Composable
fun FriendListScreen(
    onBackClick: () -> Unit,
    onCallItemClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    // Dummy data for the friend list
    val dummyFriends = remember {
        listOf(
            FriendCallItem(1, "내가만든도깨비", R.drawable.lantern_image, "발신전화", "10:25 am", true),
            FriendCallItem(2, "내가만든도깨비", R.drawable.lantern_image, "발신전화", "10:25 am", true),
            FriendCallItem(3, "귀요미", R.drawable.lantern_image, "부재중전화", "10:20 am", true),
            FriendCallItem(4, "백성욱", R.drawable.lantern_image, "발신전화", "어제", false),
            FriendCallItem(5, "박인민", R.drawable.lantern_image, "부재중전화", "어제", false),
            FriendCallItem(6, "전세라1", R.drawable.lantern_image, "발신전화", "어제", false),
            FriendCallItem(7, "전세라2", R.drawable.lantern_image, "부재중전화", "어제", false),
            FriendCallItem(8, "김철수", R.drawable.lantern_image, "발신전화", "어제", false),
            FriendCallItem(9, "이영희", R.drawable.lantern_image, "부재중전화", "어제", false),
            FriendCallItem(10, "박지성", R.drawable.lantern_image, "발신전화", "2일 전", false),
            FriendCallItem(11, "손흥민", R.drawable.lantern_image, "부재중전화", "2일 전", false),
            FriendCallItem(12, "김민재", R.drawable.lantern_image, "발신전화", "2일 전", false),
            FriendCallItem(13, "황희찬", R.drawable.lantern_image, "부재중전화", "3일 전", false),
            FriendCallItem(14, "이강인", R.drawable.lantern_image, "발신전화", "3일 전", false),
            FriendCallItem(15, "조규성", R.drawable.lantern_image, "부재중전화", "3일 전", false)
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(Color.Black)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "최근 통화",
                color = Color.White,
                fontSize = 35.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Color(0xFF333333), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "검색",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Friend List with Scroll
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {
            items(dummyFriends) { friend ->
                Box(
                    modifier = Modifier
                        .clickable { onCallItemClick() }
                ) {
                    FriendCallItem(friend)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        
        // Bottom Navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(Color.Black),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Phone Icon (Selected)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFFD700), CircleShape)
                        .padding(8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_call),
                        contentDescription = "Calls",
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 선택 표시
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(4.dp)
                        .background(Color(0xFFFFD700), CircleShape)
                )
            }
            
            // Home Icon
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Home",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // Profile Icon
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onProfileClick() }
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FriendListScreenPreview() {
    LanternTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            FriendListScreen(
                onBackClick = {},
                onCallItemClick = {},
                onProfileClick = {}
            )
        }
    }
}
