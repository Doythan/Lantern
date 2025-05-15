package com.ssafy.lanterns.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 랜턴 그라디언트 정의
val lanternGradient = Brush.sweepGradient(
    colors = listOf(LanternTeal, LanternBlue, LanternViolet, LanternTeal)
)

// 다크 모드만 사용합니다 (앱 전체가 다크 테마 기반)
private val AppColorScheme = darkColorScheme(
    primary = ButtonBlue,
    secondary = BleAccent,
    tertiary = BleBlue2,
    background = NavyTop,
    surface = NavyBottom,
    onPrimary = TextWhite,
    onSecondary = TextWhite,
    onTertiary = TextWhite,
    onBackground = TextWhite,
    onSurface = TextWhite70,
    error = ErrorRed,
    onError = TextWhite
)

@Composable
fun LanternTheme(
    // 항상 다크 테마를 사용하므로 darkTheme 파라미터 제거
    // dynamicColor는 여전히 유지 (다크 모드 기준으로 동적 색상 적용 가능)
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            // 항상 다크 모드 기준 동적 색상 사용
            dynamicDarkColorScheme(context)
        }
        else -> AppColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 완전 투명한 상태바 설정
            window.statusBarColor = Color.Transparent.toArgb()
            // 네비게이션 바도 투명하게 설정 (선택 사항)
            window.navigationBarColor = Color.Transparent.toArgb()
            // 항상 라이트 모드 설정 해제 (다크 모드 기준으로 아이콘 색상 조정)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            // 시스템 영역까지 앱 콘텐츠 확장
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}