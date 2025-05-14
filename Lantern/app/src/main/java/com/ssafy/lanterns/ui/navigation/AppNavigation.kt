package com.ssafy.lanterns.ui.navigation

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ssafy.lanterns.ui.common.MainScaffold // MainScaffold 임포트
import com.ssafy.lanterns.ui.screens.call.FriendListScreen
import com.ssafy.lanterns.ui.screens.call.IncomingCallScreen
import com.ssafy.lanterns.ui.screens.call.OngoingCallScreen
import com.ssafy.lanterns.ui.screens.call.OutgoingCallScreen
// import com.ssafy.lanterns.ui.screens.chat.ChatScreen // 삭제된 파일 임포트 제거
import com.ssafy.lanterns.ui.screens.chat.ChatListScreen
import com.ssafy.lanterns.ui.screens.chat.DirectChatScreen
import com.ssafy.lanterns.ui.screens.chat.PublicChatScreen
import com.ssafy.lanterns.ui.screens.common.ProfileScreen // 프로필 화면 임포트
import com.ssafy.lanterns.ui.screens.common.UserProfileData // 프로필 데이터 클래스 임포트
import com.ssafy.lanterns.ui.screens.login.LoginScreen
import com.ssafy.lanterns.ui.screens.main.MainScreen
import com.ssafy.lanterns.ui.screens.mypage.MyPageScreen
import com.ssafy.lanterns.ui.screens.signup.SignupScreen

// 네비게이션 라우트 정의
object AppDestinations {
    const val LOGIN_ROUTE = "login"
    const val SIGNUP_ROUTE = "signup"
    // const val CHAT_ROUTE = "chat" // 삭제된 화면에 대한 라우트 제거
    const val MYPAGE_ROUTE = "mypage"
    const val FRIENDLIST_ROUTE = "friendlist" // 통화 목록 (하단 탭)
    const val INCOMING_CALL_ROUTE = "incomingcall"
    const val ONGOING_CALL_ROUTE = "ongoingcall"
    const val OUTGOING_CALL_ROUTE = "outgoingcall/{receiverId}" // 통화 거는 중 화면 (receiverId 파라미터 포함)
    const val OUTGOING_CALL_ARG_RECEIVER_ID = "receiverId"
    const val HOME_ROUTE = "home" // 채팅 목록 (하단 탭) - 기존 메인 역할
    const val MAIN_SCREEN_ROUTE = "main_screen" // 새로운 메인 화면

    // 새로운 채팅 라우트
    const val PUBLIC_CHAT_ROUTE = "public_chat"
    const val DIRECT_CHAT_ROUTE = "direct_chat/{userId}" // 사용자 ID 파라미터 포함
    const val DIRECT_CHAT_ARG_USER_ID = "userId"

    // 프로필 화면 라우트
    const val PROFILE_ROUTE = "profile/{userId}/{name}/{distance}"
    const val PROFILE_ARG_USER_ID = "userId"
    const val PROFILE_ARG_NAME = "name"
    const val PROFILE_ARG_DISTANCE = "distance"

    // 다른 라우트 추가 가능
}

/**
 * 앱 전체 네비게이션 구조 (Jetpack Navigation Compose 사용)
 */
@Composable
fun AppNavigation() {
    // NavController 생성
    val navController = rememberNavController()

    // NavHost 설정
    NavHost(navController = navController, startDestination = AppDestinations.LOGIN_ROUTE) {
        // 로그인 화면
        composable(AppDestinations.LOGIN_ROUTE) {
            LoginScreen(
                onSignUpClick = { navController.navigate(AppDestinations.SIGNUP_ROUTE) },
                // 아래 클릭 핸들러들은 로그인 성공 시 이동으로 대체될 수 있음
                onMyPageClick = { navController.navigate(AppDestinations.MYPAGE_ROUTE) },
                onFriendListClick = { navController.navigate(AppDestinations.FRIENDLIST_ROUTE) },
                onIncomingCallClick = { navController.navigate(AppDestinations.INCOMING_CALL_ROUTE) }, // 테스트용?
                onHomeClick = { navController.navigate(AppDestinations.HOME_ROUTE) }, // 주석 해제
                onLoginSuccess = {
                    // 로그인 성공 시 MAIN_SCREEN_ROUTE로 이동하고 로그인 화면은 백스택에서 제거
                    navController.navigate(AppDestinations.MAIN_SCREEN_ROUTE) {
                        popUpTo(AppDestinations.LOGIN_ROUTE) { inclusive = true }
                    }
                }
            )
        }

        // 회원가입 화면
        composable(AppDestinations.SIGNUP_ROUTE) {
            SignupScreen(
                onBackToLoginClick = { navController.popBackStack() }
            )
        }
        
        // 새로운 메인 화면 (네비게이션 바 포함)
        composable(AppDestinations.MAIN_SCREEN_ROUTE) {
            MainScaffold(navController = navController) { paddingValues ->
                MainScreen(paddingValues = paddingValues)
            }
        }

        // 채팅 상세 화면 (개별 채팅방) 라우트 제거 - 이제 사용하지 않음
        // composable(AppDestinations.CHAT_ROUTE) {
        //     // ChatScreen은 MainScaffold 밖에 있어야 하단 네비게이션이 보이지 않음
        //     ChatScreen(
        //         // 필요 시 navController, chatId 등 전달
        //     )
        // }

        // --- 하단 네비게이션 바가 있는 화면들 ---

        // 홈(채팅 목록) 화면 (네비게이션 바 포함)
        composable(AppDestinations.HOME_ROUTE) {
            MainScaffold(navController = navController) { paddingValues ->
                ChatListScreen(
                    paddingValues = paddingValues,
                    navController = navController // NavController 전달
                )
            }
        }

        // 통화 목록 화면 (네비게이션 바 포함)
        composable(AppDestinations.FRIENDLIST_ROUTE) {
            MainScaffold(navController = navController) { paddingValues ->
                FriendListScreen(
                    onBackClick = { navController.popBackStack() }, // 현재 구조상 필요 없을 수 있음
                    onCallItemClick = { navController.navigate(AppDestinations.ONGOING_CALL_ROUTE) }, // 통화 아이템 클릭 시 통화 중 화면으로 이동 (예시)
                    onProfileClick = { navController.navigate(AppDestinations.MYPAGE_ROUTE) }, // 프로필 클릭 시 마이페이지로 이동 (예시)
                    paddingValues = paddingValues // Scaffold 패딩 전달
                )
            }
        }

        // 마이페이지 화면 (네비게이션 바 포함)
        composable(AppDestinations.MYPAGE_ROUTE) {
            MainScaffold(navController = navController) { paddingValues ->
                MyPageScreen(
                    onNavigateToLogin = { // 로그아웃 처리
                        navController.navigate(AppDestinations.LOGIN_ROUTE) {
                            popUpTo(navController.graph.id) { // 전체 백스택 클리어
                                inclusive = true
                            }
                            launchSingleTop = true // 로그인 화면 중복 생성 방지
                        }
                    },
                    paddingValues = paddingValues // Scaffold 패딩 전달
                )
            }
        }

        // --- 하단 네비게이션 바가 없는 화면들 (계속) ---

        // 전화 수신 화면
        composable(AppDestinations.INCOMING_CALL_ROUTE) {
             IncomingCallScreen(
                 callerName = "임시 발신자", // 실제 데이터 전달 필요
                 onRejectClick = { navController.popBackStack() }, // 거절 시 이전 화면으로
                 onAcceptClick = { // 수락 시 통화 중 화면으로 이동하고 수신 화면은 제거
                     navController.navigate(AppDestinations.ONGOING_CALL_ROUTE) {
                         popUpTo(AppDestinations.INCOMING_CALL_ROUTE) { inclusive = true }
                     }
                 }
             )
        }

        // 통화 거는 중 화면
        composable(
            route = AppDestinations.OUTGOING_CALL_ROUTE,
            arguments = listOf(navArgument(AppDestinations.OUTGOING_CALL_ARG_RECEIVER_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val receiverId = backStackEntry.arguments?.getString(AppDestinations.OUTGOING_CALL_ARG_RECEIVER_ID)
            if (receiverId != null) {
                OutgoingCallScreen(
                    receiverName = "수신자", // 실제 데이터로 대체 필요
                    receiverId = receiverId.toIntOrNull() ?: 1,
                    onCancelClick = {
                        navController.popBackStack() // 통화 취소 시 이전 화면으로
                    }
                )
            } else {
                Text("Error: Receiver ID not found.")
            }
        }

        // 통화 중 화면
        composable(AppDestinations.ONGOING_CALL_ROUTE) {
            OngoingCallScreen(
                 callerName = "임시 발신자", // 실제 데이터 전달 필요
                 onEndCallClick = {
                     // 통화 종료 후 MAIN_SCREEN_ROUTE로 이동
                     navController.navigate(AppDestinations.MAIN_SCREEN_ROUTE){
                         popUpTo(AppDestinations.LOGIN_ROUTE) // 로그인 이후의 모든 화면 제거
                     }
                 }
             )
        }

        // 공용 채팅 화면
        composable(AppDestinations.PUBLIC_CHAT_ROUTE) {
            PublicChatScreen(
                navController = navController // NavController 전달
            )
        }

        // 1:1 채팅 화면 (userId 인자 받음)
        composable(
            route = AppDestinations.DIRECT_CHAT_ROUTE,
            arguments = listOf(navArgument(AppDestinations.DIRECT_CHAT_ARG_USER_ID) { type = NavType.StringType }) // Int 타입이면 NavType.IntType
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString(AppDestinations.DIRECT_CHAT_ARG_USER_ID)
            if (userId != null) {
                DirectChatScreen(
                     userId = userId,
                     navController = navController // NavController 전달
                )
            } else {
                Text("Error: User ID not found.")
            }
        }

        // 프로필 화면
        composable(
            route = AppDestinations.PROFILE_ROUTE,
            arguments = listOf(
                navArgument(AppDestinations.PROFILE_ARG_USER_ID) { type = NavType.StringType },
                navArgument(AppDestinations.PROFILE_ARG_NAME) { type = NavType.StringType },
                navArgument(AppDestinations.PROFILE_ARG_DISTANCE) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString(AppDestinations.PROFILE_ARG_USER_ID) ?: ""
            val name = backStackEntry.arguments?.getString(AppDestinations.PROFILE_ARG_NAME) ?: ""
            val distance = backStackEntry.arguments?.getString(AppDestinations.PROFILE_ARG_DISTANCE) ?: ""
            
            ProfileScreen(
                navController = navController,
                userData = UserProfileData(
                    userId = userId,
                    name = name,
                    distance = distance
                )
            )
        }

        // 다른 화면들에 대한 composable 추가 가능
    }
}