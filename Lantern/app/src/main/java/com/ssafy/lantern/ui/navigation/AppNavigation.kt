package com.ssafy.lantern.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.ssafy.lantern.ui.screens.call.CallHistoryScreen
import com.ssafy.lantern.ui.screens.call.FriendListScreen
import com.ssafy.lantern.ui.screens.call.IncomingCallScreen
import com.ssafy.lantern.ui.screens.call.OngoingCallScreen
import com.ssafy.lantern.ui.screens.login.LoginScreen
import com.ssafy.lantern.ui.screens.mypage.MyPageScreen
import com.ssafy.lantern.ui.screens.signup.SignupScreen

/**
 * 앱 전체 네비게이션 구조
 * 현재는 when 문으로 구현되어 있지만 추후 Navigation Compose로 변경 가능
 */
@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf("login") }
    
    when (currentScreen) {
        "login" -> LoginScreen(
            onSignUpClick = { currentScreen = "signup" },
            onMyPageClick = { currentScreen = "mypage" },
            onFriendListClick = { currentScreen = "friendlist" },
            onIncomingCallClick = { currentScreen = "incomingcall" }
        )
        "signup" -> SignupScreen(
            onBackToLoginClick = { currentScreen = "login" }
        )
        "mypage" -> MyPageScreen(
            onBackClick = { currentScreen = "login" }
        )
        "friendlist" -> FriendListScreen(
            onBackClick = { currentScreen = "login" },
            onCallItemClick = { currentScreen = "ongoingcall" },
            onProfileClick = { currentScreen = "mypage" }
        )
        "incomingcall" -> IncomingCallScreen(
            callerName = "도경원",
            onRejectClick = { currentScreen = "login" },
            onAcceptClick = { currentScreen = "ongoingcall" }
        )
        "ongoingcall" -> OngoingCallScreen(
            callerName = "도경원",
            onEndCallClick = { currentScreen = "login" }
        )
    }
}