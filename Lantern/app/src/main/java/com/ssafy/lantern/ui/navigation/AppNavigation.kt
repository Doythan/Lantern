package com.ssafy.lantern.ui.navigation

import androidx.compose.runtime.Composable
// import androidx.compose.runtime.mutableStateOf // 사용 안 함
// import androidx.compose.runtime.remember // 사용 안 함
// import androidx.compose.runtime.getValue // 사용 안 함
// import androidx.compose.runtime.setValue // 사용 안 함
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ssafy.lantern.ui.screens.call.CallHistoryScreen // 필요 시 사용
import com.ssafy.lantern.ui.screens.call.FriendListScreen
import com.ssafy.lantern.ui.screens.call.IncomingCallScreen
import com.ssafy.lantern.ui.screens.call.OngoingCallScreen
import com.ssafy.lantern.ui.screens.chat.ChatScreen // ChatScreen 임포트
import com.ssafy.lantern.ui.screens.login.LoginScreen
// import com.ssafy.lantern.ui.screens.main.PlaceholderMainScreen // 임시 메인 화면 제거
import com.ssafy.lantern.ui.screens.mypage.MyPageScreen
import com.ssafy.lantern.ui.screens.signup.SignupScreen
import com.ssafy.lantern.ui.screens.main.PlaceholderMainScreen
import com.ssafy.lantern.ui.common.MainScaffold // MainScaffold 임포트 추가

// 네비게이션 라우트 정의
object AppDestinations {
    const val LOGIN_ROUTE = "login"
    const val SIGNUP_ROUTE = "signup"
    // const val PLACEHOLDER_MAIN_ROUTE = "placeholder_main" // 제거
    const val CHAT_ROUTE = "chat" // 채팅 화면 라우트 추가
    const val MYPAGE_ROUTE = "mypage"
    const val FRIENDLIST_ROUTE = "friendlist"
    const val INCOMING_CALL_ROUTE = "incomingcall"
    const val ONGOING_CALL_ROUTE = "ongoingcall"
    const val HOME_ROUTE = "home"
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
                // 각 버튼 클릭 시 해당하는 라우트로 이동
                onSignUpClick = { navController.navigate(AppDestinations.SIGNUP_ROUTE) },
                onMyPageClick = { navController.navigate(AppDestinations.MYPAGE_ROUTE) }, // 로그인 상태 확인 필요할 수 있음
                onFriendListClick = { navController.navigate(AppDestinations.FRIENDLIST_ROUTE) }, // 로그인 상태 확인 필요할 수 있음
                onIncomingCallClick = { navController.navigate(AppDestinations.INCOMING_CALL_ROUTE) },
                onHomeClick = { navController.navigate(AppDestinations.HOME_ROUTE) },
                // 로그인 성공 시 홈 화면으로 이동
                onLoginSuccess = {
                    navController.navigate(AppDestinations.HOME_ROUTE) { // HOME_ROUTE로 변경
                        popUpTo(AppDestinations.LOGIN_ROUTE) { inclusive = true }
                    }
                }
            )
        }

        // 회원가입 화면
        composable(AppDestinations.SIGNUP_ROUTE) {
            SignupScreen(
                // 뒤로 가기 (로그인 화면으로)
                onBackToLoginClick = { navController.popBackStack() }
            )
        }

        // 채팅 화면 추가
        composable(AppDestinations.CHAT_ROUTE) {
            ChatScreen(
                // 필요 시 navController 등 전달
            )
        }

        // 홈 화면 (네비게이션 바 포함)
        composable(AppDestinations.HOME_ROUTE) {
            MainScaffold(navController = navController) { paddingValues -> // MainScaffold로 감싸기
                PlaceholderMainScreen(
                    // PlaceholderMainScreen은 이제 paddingValues를 사용할 수 있음 (필요하다면)
                    // Modifier.padding(paddingValues) 등을 적용 가능
                )
            }
        }

        // 마이페이지 화면 (네비게이션 바 포함)
        composable(AppDestinations.MYPAGE_ROUTE) {
            MainScaffold(navController = navController) { paddingValues -> // MainScaffold에서 paddingValues 받음
                MyPageScreen(
                    // ViewModel은 Hilt가 주입
                    popBackStack = { navController.popBackStack() }, // onBackClick 대신 popBackStack 전달
                    onNavigateToLogin = { // onNavigateToLogin 콜백 전달
                        // 로그아웃 시 로그인 화면으로 이동하고 백스택 모두 제거
                        navController.navigate(AppDestinations.LOGIN_ROUTE) {
                            popUpTo(navController.graph.id) { // 전체 그래프까지 popUp
                                inclusive = true
                            }
                            launchSingleTop = true // 로그인 화면 중복 방지
                        }
                    },
                    paddingValues = paddingValues // Scaffold 패딩 전달
                )
            }
        }

        // 친구 목록 (최근 통화) 화면 (네비게이션 바 포함)
        composable(AppDestinations.FRIENDLIST_ROUTE) {
            MainScaffold(navController = navController) { paddingValues ->
                FriendListScreen(
                    onBackClick = { navController.popBackStack() },
                    onCallItemClick = { navController.navigate(AppDestinations.ONGOING_CALL_ROUTE) },
                    onProfileClick = { navController.navigate(AppDestinations.MYPAGE_ROUTE) }
                     // Modifier.padding(paddingValues) 적용 가능
                )
            }
        }

        // 전화 수신 화면
        composable(AppDestinations.INCOMING_CALL_ROUTE) {
             IncomingCallScreen(
                 callerName = "도경원", // 실제 데이터 전달 필요
                 onRejectClick = { navController.popBackStack() }, // 또는 다른 로직
                 onAcceptClick = { navController.navigate(AppDestinations.ONGOING_CALL_ROUTE) { popUpTo(AppDestinations.INCOMING_CALL_ROUTE) {inclusive = true}} } // 수신 화면 제거
             )
        }

        // 통화 중 화면
        composable(AppDestinations.ONGOING_CALL_ROUTE) {
            OngoingCallScreen(
                 callerName = "도경원", // 실제 데이터 전달 필요
                 // 통화 종료 후 채팅 화면으로 가도록 수정 (예시)
                 onEndCallClick = { navController.navigate(AppDestinations.CHAT_ROUTE) { popUpTo(AppDestinations.LOGIN_ROUTE)} } // 백스택 관리 고려
             )
        }

        // 다른 화면들에 대한 composable 추가 가능
    }
}