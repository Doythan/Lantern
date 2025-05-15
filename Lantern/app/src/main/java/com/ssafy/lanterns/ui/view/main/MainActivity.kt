package com.ssafy.lanterns.ui.view.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.ssafy.lanterns.ui.navigation.AppNavigation
import com.ssafy.lanterns.ui.theme.LanternTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 상태바, 내비게이션 바 투명하게 처리하고 전체화면 모드로 설정
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            LanternTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // App() 대신 직접 AppNavigation()을 호출하여 시작 화면을 표시합니다.
                    AppNavigation()
                }
            }
        }
    }
}