package com.ssafy.lanterns.ui.theme

import androidx.compose.ui.graphics.Color

// 기본 머티리얼 테마 색상 (레거시 코드와의 호환성을 위해 유지)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// 앱 기본 테마 색상
val AppBackground = Color(0xFF0A0F2C)      // 기본 앱 배경 (기존 NavyBottom 유지 또는 약간 조정)
val PrimaryBlue = Color(0xFF0082FC)       // 주요 인터랙션 색상 (기존 ButtonBlue)
val TextPrimary = Color(0xFFFFFFFF)       // 기본 텍스트 색상 (기존 TextWhite)
val TextSecondary = Color(0xB3FFFFFF)     // 보조 텍스트 색상 (기존 TextWhite70)
val SurfaceDark = Color(0xFF0D1D3A)       // 카드, 입력창 등 표면 색상 (기존 NavyMedium)
val ErrorRed = Color(0xFFFF5252)          // 오류 발생 시 색상

// 랜턴 전용 색상 추가
val LanternCoreYellow = Color(0xFFFFE082) // 랜턴 중심부 밝은 노란색
val LanternGlowOrange = Color(0xFFFFB74D) // 랜턴 빛 번짐 주황색
val LanternWarmWhite = Color(0xFFFFF9C4)  // 랜턴 하이라이트 따뜻한 흰색
val LanternShadeDark = Color(0xFF212121)  // 어두운 배경 (기존 이미지와 유사하거나 더 어둡게)
val LanternParticleColor = Color(0xFFFFE57F) // 빛 입자 색상

// 새로 추가된 색상
val DarkBackground = LanternShadeDark // 또는 NavyTop
val DarkCardBackground = SurfaceDark
val Primary = PrimaryBlue
val Error = ErrorRed

// BLE 관련 색상
val BleBlue1 = Color(0xFF0057FF)      // 깊은 파란색 (기본 색상)
val BleBlue2 = Color(0xFF00C6FF)      // 밝은 시안 계열 (강조 색상)
val BleAccentBright = Color(0xFF4DFFB4)     // 네온 민트 (주요 강조 색상) - 기존 색상
val BleAccent = Color(0xFF21AA73)     // 진한 민트 (주요 강조 색상) - 명도 낮춘 버전
val BleDarkBlue = Color(0xFF003380)   // 어두운 파란색 (외곽선)
val BleGlow = Color(0x800082FC)       // 빛나는 효과용 (PrimaryBlue에 alpha를 적용한 색상)

// 채팅 UI 색상
val ChatBubbleMine = Color(0xFF1A2C51)      // 내 메시지 배경색 (기존보다 약간 밝거나 따뜻한 톤 고려)
val ChatBubbleOthers = Color(0xFF2A3F6D)    // 타인 메시지 배경색 (기존보다 약간 밝거나 따뜻한 톤 고려)
val ChatInputBackground = SurfaceDark     // 입력창 배경색

// 연결 강도 관련 색상 (거리 기반)
val ConnectionNear = Color(0xFF21AA73)    // 가까운 거리 (0-100m, 초록색)
val ConnectionMedium = Color(0xFFFFD700)  // 중간 거리 (100-300m, 노란색)
val ConnectionFar = ErrorRed

// 기존 연결 강도 색상 리네이밍 (호환성 유지)
val ConnectionStrong = ConnectionNear
val ConnectionWeak = ConnectionFar

// 기존 색상 이름 호환성을 위한 별칭
val NavyTop = AppBackground
val NavyBottom = AppBackground
val NavyMedium = SurfaceDark
val ButtonBlue = PrimaryBlue
val TextWhite = TextPrimary
val TextWhite70 = TextSecondary

// 랜턴 테마 색상 (Theme.kt에서 사용)
val LanternTeal = Color(0xFF4DB6AC)
val LanternBlue = Color(0xFF42A5F5)
val LanternViolet = Color(0xFF7E57C2) 