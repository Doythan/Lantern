package com.ssafy.lanterns.ui.screens.chat

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
// BluetoothComponents.kt 파일의 함수 import 추가
import com.ssafy.lanterns.ui.screens.chat.requestBluetoothPermissions

/**
 * 이 파일은 더 이상 사용되지 않습니다.
 * PublicChatScreen.kt와 DirectChatScreen.kt 파일로 대체되었으며, 
 * 실제 채팅 기능은 해당 파일들에서 구현되어 있습니다.
 * 
 * 이 파일은 호환성을 위해 유지되며 PublicChatScreen으로 리다이렉트합니다.
 * 새로운 기능을 추가할 때는 PublicChatScreen.kt 또는 DirectChatScreen.kt를 수정하세요.
 */
@Composable
fun ChatScreen(
    navController: NavController,
    viewModel: ChatViewModel = hiltViewModel()
) {
    Log.d("ChatScreen", "Redirecting to PublicChatScreen")
    PublicChatScreen(navController = navController, viewModel = viewModel)
}