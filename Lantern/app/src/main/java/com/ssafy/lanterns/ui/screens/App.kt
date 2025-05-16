package com.ssafy.lanterns.ui.screens

import android.util.Log // 디버깅 로그를 위해 추가
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
// import com.ssafy.lanterns.ui.screens.ondevice.OnDeviceAIScreen // OnDeviceAIDialog를 사용하므로 직접 호출하지 않음
import com.ssafy.lanterns.ui.screens.ondevice.OnDeviceAIDialog // 수정: OnDeviceAIDialog 임포트
import com.ssafy.lanterns.ui.screens.main.MainViewModel

/**
 * 앱의 루트 컴포넌트
 * 내비게이션 및 모달 오버레이를 포함합니다.
 * MainActivity의 setContent에서 이 함수가 호출될 가능성이 높습니다.
 */
@Composable
fun App() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AppContent() // AppContent를 호출하여 실제 UI를 구성합니다.
    }
}

/**
 * 앱의 주요 콘텐츠를 구성하는 Composable.
 * AppNavigation과 조건부 OnDeviceAIDialog를 포함합니다.
 */
@Composable
fun AppContent(
    mainViewModel: MainViewModel = hiltViewModel() // MainViewModel 주입
) {
    // MainViewModel로부터 aiActive 상태를 관찰합니다.
    // 이 상태는 WakeWordService에 의해 "헤이 랜턴"이 감지되면 true로 변경됩니다.
    val aiActive by mainViewModel.aiActive.collectAsState()
    Log.d("AppContent", "현재 aiActive 상태: $aiActive") // 디버깅: aiActive 상태 변경 확인

    Box(modifier = Modifier.fillMaxSize()) {
        // 기본 앱 네비게이션 (일반 화면들이 여기에 표시됨)
        AppNavigation(modifier = Modifier.fillMaxSize())

        // aiActive 상태가 true이면 OnDeviceAIDialog를 표시합니다.
        // 이 다이얼로그는 AppNavigation 위에 오버레이 되어 모든 화면에서 나타날 수 있습니다.
        if (aiActive) {
            Log.d("AppContent", "조건문 통과: aiActive가 true이므로 OnDeviceAIDialog 표시 시도.")
            OnDeviceAIDialog(
                onDismiss = {
                    Log.d("AppContent", "OnDeviceAIDialog.onDismiss 호출됨. mainViewModel.deactivateAI() 실행.")
                    mainViewModel.deactivateAI()
                }
            )
        } else {
            Log.d("AppContent", "조건문 실패: aiActive가 false이므로 OnDeviceAIDialog 표시 안 함.")
        }
    }
}