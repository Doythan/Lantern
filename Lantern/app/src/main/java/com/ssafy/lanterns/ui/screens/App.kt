package com.ssafy.lanterns.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ssafy.lanterns.ui.navigation.AppNavigation
import com.ssafy.lanterns.ui.screens.ondevice.OnDeviceAIScreen
import com.ssafy.lanterns.ui.screens.main.MainViewModel

/**
 * 앱의 루트 컴포넌트
 * 내비게이션 및 모달 오버레이를 포함합니다.
 */
@Composable
fun App() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AppContent()
    }
}

@Composable
fun AppContent(
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val aiActive by mainViewModel.aiActive.collectAsState()
    
    Box(modifier = Modifier.fillMaxSize()) {
        // 기본 앱 네비게이션
        AppNavigation(modifier = Modifier.fillMaxSize())
        
        // AI 활성화 시 전체 화면으로 표시되는 OnDeviceAIScreen
        if (aiActive) {
            OnDeviceAIScreen(
                onDismiss = { mainViewModel.deactivateAI() }
            )
        }
    }
} 