package com.ssafy.lanterns.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.ssafy.lanterns.ui.navigation.AppBottomNavigationBar
import com.ssafy.lanterns.ui.theme.NavyTop

/**
 * 앱의 주요 화면에 공통으로 사용되는 스캐폴드 컴포넌트
 * 하단 네비게이션 바를 포함하고 자식 콘텐츠에 적절한 패딩 값을 전달합니다.
 *
 * @param navController 네비게이션 컨트롤러
 * @param content 스캐폴드 내부에 표시될 콘텐츠
 */
@Composable
fun MainScaffold(
    navController: NavHostController,
    content: @Composable (PaddingValues) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            bottomBar = { 
                AppBottomNavigationBar(navController = navController) 
            },
            containerColor = NavyTop // 배경 색상을 테마에 맞게 설정
        ) { innerPadding ->
            content(innerPadding) // Scaffold 내부 패딩을 콘텐츠에 전달
        }
    }
} 