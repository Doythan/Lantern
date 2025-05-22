package com.ssafy.lanterns.ui.navigation

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ssafy.lanterns.ui.common.MainScaffold
import com.ssafy.lanterns.ui.screens.call.CallHistoryScreen
import com.ssafy.lanterns.ui.screens.call.CallState
import com.ssafy.lanterns.ui.screens.call.CallViewModel
import com.ssafy.lanterns.ui.screens.call.IncomingCallScreen
import com.ssafy.lanterns.ui.screens.call.OngoingCallScreen
import com.ssafy.lanterns.ui.screens.call.OutgoingCallScreen
import com.ssafy.lanterns.ui.screens.chat.ChatListScreen
import com.ssafy.lanterns.ui.screens.chat.DirectChatScreen
import com.ssafy.lanterns.ui.screens.chat.PublicChatScreen
import com.ssafy.lanterns.ui.screens.common.ProfileScreen
import com.ssafy.lanterns.ui.screens.common.UserProfileData
import com.ssafy.lanterns.ui.screens.login.LoginScreen
import com.ssafy.lanterns.ui.screens.main.MainScreen
import com.ssafy.lanterns.ui.screens.mypage.MyPageScreen
import com.ssafy.lanterns.ui.screens.ondevice.OnDeviceAIScreen
import com.ssafy.lanterns.ui.screens.splash.SplashScreen
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay

// 네비게이션 라우트 정의
object AppDestinations {
    const val SPLASH_ROUTE = "splash"
    const val LOGIN_ROUTE = "login"
    const val MYPAGE_ROUTE = "mypage"
    const val FRIENDLIST_ROUTE = "friendlist"
    const val INCOMING_CALL_ROUTE = "incomingcall"
    const val ONGOING_CALL_ROUTE = "ongoingcall"
    const val OUTGOING_CALL_ROUTE = "outgoingcall/{receiverId}"
    const val OUTGOING_CALL_ARG_RECEIVER_ID = "receiverId"
    const val HOME_ROUTE = "home"
    const val MAIN_SCREEN_ROUTE = "main_screen"
    const val ONDEVICE_AI_ROUTE = "ondevice_ai"

    const val PUBLIC_CHAT_ROUTE = "public_chat"
    const val DIRECT_CHAT_ROUTE = "direct_chat/{userId}"
    const val DIRECT_CHAT_ARG_USER_ID = "userId"

    const val PROFILE_ROUTE = "profile/{userId}/{name}/{distance}"
    const val PROFILE_ARG_USER_ID = "userId"
    const val PROFILE_ARG_NAME = "name"
    const val PROFILE_ARG_DISTANCE = "distance"

    const val CALL_HISTORY_ROUTE = "call_history"
}

/**
 * 앱 전체 네비게이션 구조
 */
@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    // NavController 생성
    val navController = rememberNavController()

    // CallViewModel 인스턴스 생성
    val callViewModel: CallViewModel = hiltViewModel()

    // 통화 상태를 관찰하여 수신 화면으로 자동 전환
    val callUiState by callViewModel.uiState.collectAsState()
    LaunchedEffect(callUiState.callState) {
        if (callUiState.callState == CallState.INCOMING_CALL) {
            // 이미 수신화면이 아닐 경우에만 네비게이션
            if (navController.currentDestination?.route != AppDestinations.INCOMING_CALL_ROUTE) {
                navController.navigate(AppDestinations.INCOMING_CALL_ROUTE) {
                    // 중요: 어디서든지 수신화면으로 바로 이동할 수 있도록 설정
                    popUpTo(navController.graph.startDestinationId) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    // NavHost 설정 - 시작 화면을 스플래시 화면으로 변경
    NavHost(
        navController = navController,
        startDestination = AppDestinations.SPLASH_ROUTE,
        modifier = modifier.fillMaxSize()
    ) {
        // 스플래시 스크린 라우트
        composable(route = AppDestinations.SPLASH_ROUTE) {
            SplashScreen { isLoggedIn ->
                // 로그인 상태에 따라 다음 화면으로 이동
                if (isLoggedIn) {
                    navController.navigate(AppDestinations.MAIN_SCREEN_ROUTE) {
                        popUpTo(AppDestinations.SPLASH_ROUTE) { inclusive = true }
                    }
                } else {
                    navController.navigate(AppDestinations.LOGIN_ROUTE) {
                        popUpTo(AppDestinations.SPLASH_ROUTE) { inclusive = true }
                    }
                }
            }
        }

        // 로그인 스크린 라우트
        composable(route = AppDestinations.LOGIN_ROUTE) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(AppDestinations.MAIN_SCREEN_ROUTE) {
                        popUpTo(AppDestinations.LOGIN_ROUTE) { inclusive = true }
                    }
                }
            )
        }

        // 메인 화면
        composable(AppDestinations.MAIN_SCREEN_ROUTE) {
            // Activity 의존성을 제거했으므로 initialize 호출 제거
            MainScaffold(navController = navController) { paddingValues ->
                MainScreen(
                    paddingValues = paddingValues,
                    navController = navController,
                    callViewModel = callViewModel
                )
            }
        }

        // 통화 기록 화면
        composable(AppDestinations.CALL_HISTORY_ROUTE) {
            MainScaffold(navController = navController) { paddingValues ->
                CallHistoryScreen(
                    navController = navController,
                    paddingValues = paddingValues,
                    onCallItemClick = { callerId ->
                        // 통화 발신 화면으로 이동하면서 CallViewModel에 타겟 ID 전달
                        callViewModel.requestCallToId(callerId.toString())
                        navController.navigate(AppDestinations.OUTGOING_CALL_ROUTE.replace("{receiverId}", callerId.toString()))
                    }
                )
            }
        }


        // 마이페이지 화면
        composable(AppDestinations.MYPAGE_ROUTE) {
            MainScaffold(navController = navController) { paddingValues ->
                MyPageScreen(
                    onNavigateToLogin = {
                        navController.navigate(AppDestinations.LOGIN_ROUTE) {
                            popUpTo(navController.graph.id) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    },
                    paddingValues = paddingValues
                )
            }
        }

        // 전화 수신 화면
        composable(AppDestinations.INCOMING_CALL_ROUTE) {
            val uiState by callViewModel.uiState.collectAsState()

            // 상태에 따른 화면 전환
            LaunchedEffect(uiState.callState) {
                when (uiState.callState) {
                    CallState.ONGOING_CALL -> {
                        navController.navigate(AppDestinations.ONGOING_CALL_ROUTE) {
                            popUpTo(AppDestinations.INCOMING_CALL_ROUTE) { inclusive = true }
                        }
                    }
                    CallState.DISCONNECTED -> {
                        callViewModel.resetCallState()
                        navController.popBackStack()
                    }
                    CallState.IDLE -> {
                        navController.popBackStack()
                    }
                    else -> {}
                }
            }

            IncomingCallScreen(
                callerName = uiState.targetPerson?.nickname ?: "알 수 없는 발신자",
                callerId = 1,
                onRejectClick = { callViewModel.endCall() },
                onAcceptClick = { callViewModel.acceptCall() }
            )
        }

        // 통화 거는 중 화면 - 수정된 부분
        composable(
            route = AppDestinations.OUTGOING_CALL_ROUTE,
            arguments = listOf(navArgument(AppDestinations.OUTGOING_CALL_ARG_RECEIVER_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val receiverId = backStackEntry.arguments?.getString(AppDestinations.OUTGOING_CALL_ARG_RECEIVER_ID)
            val uiState by callViewModel.uiState.collectAsState()

            // 상태 로깅
            DisposableEffect(Unit) {
                Log.d("AppNavigation", "OutgoingCallScreen 컴포저블 들어옴 - 현재 callState: ${uiState.callState}")
                onDispose {
                    Log.d("AppNavigation", "OutgoingCallScreen 컴포저블 나감")
                }
            }

            // 상태 변경을 감지하는 LaunchedEffect - 핵심 변경 부분
            LaunchedEffect(uiState.callState) {
                Log.d("AppNavigation", "OutgoingCallScreen - callState 변경 감지: ${uiState.callState}")

                when (uiState.callState) {
                    CallState.ONGOING_CALL -> {
                        Log.d("AppNavigation", "ONGOING_CALL 상태 전환 시작")
                        try {
                            navController.navigate(AppDestinations.ONGOING_CALL_ROUTE) {
                                popUpTo(AppDestinations.OUTGOING_CALL_ROUTE) { inclusive = true }
                                launchSingleTop = true
                            }
                            Log.d("AppNavigation", "ONGOING_CALL 상태 전환 완료")
                        } catch (e: Exception) {
                            Log.e("AppNavigation", "화면 전환 오류: ${e.message}", e)
                        }
                    }
                    CallState.DISCONNECTED -> {
                        Log.d("AppNavigation", "DISCONNECTED 상태 감지됨, 뒤로가기")
                        callViewModel.resetCallState()
                        navController.popBackStack()
                    }
                    else -> {
                        Log.d("AppNavigation", "다른 상태 감지됨: ${uiState.callState}")
                    }
                }
            }

            // 발신자 ID 설정 및 화면 표시
            LaunchedEffect(receiverId) {
                if (uiState.callState == CallState.IDLE && receiverId != null) {
                    callViewModel.requestCallToId(receiverId)
                }
            }

            if (receiverId != null) {
                OutgoingCallScreen(
                    receiverName = uiState.targetPerson?.nickname ?: "사용자",
                    receiverId = receiverId.toIntOrNull() ?: 1,
                    onCancelClick = { callViewModel.endCall() },
                    callViewModel = callViewModel  // callViewModel 전달
                )
            } else {
                Text("Error: Receiver ID not found.")
            }
        }

        // 통화 중 화면
        // 통화 중 화면
        composable(AppDestinations.ONGOING_CALL_ROUTE) {
            val uiState by callViewModel.uiState.collectAsState()

            // 중요: 화면이 나갈 때 연결이 끊어지지 않도록 함
            DisposableEffect(Unit) {
                Log.d("AppNavigation", "OngoingCallScreen 컴포저블 들어옴 - 리스너/연결 유지 모드")
                callViewModel.onScreenActive()
                onDispose {
                    Log.d("AppNavigation", "OngoingCallScreen 컴포저블 나감 - 연결 유지")
                    // 여기서 endCall이나 다른 해제 코드를 호출하지 않음
                }
            }

            // 기존 LaunchedEffect는 유지
            LaunchedEffect(uiState.callState) {
                if (uiState.callState == CallState.DISCONNECTED || uiState.callState == CallState.IDLE) {
                    Log.d("AppNavigation", "통화 종료 상태 감지, 메인 화면으로 이동")
                    callViewModel.resetCallState()
                    navController.popBackStack(AppDestinations.MAIN_SCREEN_ROUTE, false)
                }
            }

            LaunchedEffect(Unit) {
                Log.d("AppNavigation", "OngoingCallScreen 시작 - 상태 유지")
                if (uiState.callState == CallState.ONGOING_CALL && uiState.callDuration == 0) {
                    callViewModel.startCallDurationTimer()
                }
            }

            OngoingCallScreen(
                callerName = uiState.targetPerson?.nickname ?: "사용자",
                callerId = 1,
                onEndCallClick = { callViewModel.endCall() },
                callViewModel = callViewModel
            )
        }

        // 채팅 화면
        composable(AppDestinations.HOME_ROUTE) {
            MainScaffold(navController = navController) { paddingValues ->
                ChatListScreen(
                    paddingValues = paddingValues,
                    navController = navController
                )
            }
        }

        // 공용 채팅 화면
        composable(AppDestinations.PUBLIC_CHAT_ROUTE) {
            PublicChatScreen(
                navController = navController
            )
        }

        // 1:1 채팅 화면
        composable(
            route = AppDestinations.DIRECT_CHAT_ROUTE,
            arguments = listOf(navArgument(AppDestinations.DIRECT_CHAT_ARG_USER_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString(AppDestinations.DIRECT_CHAT_ARG_USER_ID)
            if (userId != null) {
                DirectChatScreen(
                    userId = userId,
                    navController = navController
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

        // 온디바이스 AI 화면
        composable(AppDestinations.ONDEVICE_AI_ROUTE) {
            OnDeviceAIScreen(
                onDismiss = { navController.popBackStack() }
            )
        }
    }
}