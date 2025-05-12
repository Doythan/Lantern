package com.ssafy.lanterns.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ssafy.lanterns.ui.theme.BleAccent
import com.ssafy.lanterns.ui.theme.NavyBottom
import com.ssafy.lanterns.ui.theme.TextWhite70

// 하단 네비게이션 아이템 정의
sealed class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Chat : BottomNavItem(
        AppDestinations.HOME_ROUTE, 
        "채팅", 
        Icons.Filled.Chat,
        Icons.Outlined.Chat
    )
    
    object Home : BottomNavItem(
        AppDestinations.MAIN_SCREEN_ROUTE, 
        "홈", 
        Icons.Filled.Home,
        Icons.Outlined.Home
    )
    
    object Settings : BottomNavItem(
        AppDestinations.MYPAGE_ROUTE, 
        "설정", 
        Icons.Filled.Settings,
        Icons.Outlined.Settings
    )
}

@Composable
fun AppBottomNavigationBar(navController: NavController) {
    val items = listOf(
        BottomNavItem.Chat,
        BottomNavItem.Home,
        BottomNavItem.Settings
    )

    // 현재 백스택 항목 가져오기
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    // 현재 화면 경로 가져오기
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        containerColor = NavyBottom,
        contentColor = TextWhite70,
        tonalElevation = 8.dp,
    ) {
        items.forEach { screen ->
            val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
            
            NavigationBarItem(
                icon = { 
                    Icon(
                        imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                        contentDescription = screen.label,
                        tint = if (isSelected) BleAccent else TextWhite70
                    ) 
                },
                label = { 
                    Text(
                        text = screen.label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) BleAccent else TextWhite70
                    ) 
                },
                selected = isSelected,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BleAccent,
                    unselectedIconColor = TextWhite70,
                    selectedTextColor = BleAccent,
                    unselectedTextColor = TextWhite70,
                    indicatorColor = NavyBottom
                ),
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
} 