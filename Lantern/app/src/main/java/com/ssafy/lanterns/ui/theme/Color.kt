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
val NavyTop = Color(0xFF051225)       // 화면 상단 배경 색상
val NavyBottom = Color(0xFF0A0F2C)    // 화면 하단 배경 색상
val NavyMedium = Color(0xFF0D1D3A)    // 중간 톤의 네이비 색상 (입력창 등에 사용)
val ButtonBlue = Color(0xFF0082FC)    // 기본 버튼 색상
val TextWhite = Color(0xFFFFFFFF)     // 기본 텍스트 색상
val TextWhite70 = Color(0xB3FFFFFF)   // 보조 텍스트 색상 (70% 불투명도)

// 새로 추가된 색상
val DarkBackground = Color(0xFF051225)      // 어두운 배경 색상 (NavyTop과 동일)
val DarkCardBackground = Color(0xFF0D1D3A)  // 어두운 카드 배경 색상 (NavyMedium과 동일)
val Primary = Color(0xFF0057FF)             // 주요 색상 (BleBlue1과 동일)
val Error = Color(0xFFFF5252)               // 오류 색상 (ConnectionFar와 동일)

// BLE 관련 색상
val BleBlue1 = Color(0xFF0057FF)      // 깊은 파란색 (기본 색상)
val BleBlue2 = Color(0xFF00C6FF)      // 밝은 시안 계열 (강조 색상)
val BleAccentBright = Color(0xFF4DFFB4)     // 네온 민트 (주요 강조 색상) - 기존 색상
val BleAccent = Color(0xFF21AA73)     // 진한 민트 (주요 강조 색상) - 명도 낮춘 버전
val BleDarkBlue = Color(0xFF003380)   // 어두운 파란색 (외곽선)
val BleGlow = Color(0x800082FC)       // 빛나는 효과용

// 채팅 UI 색상
val ChatBubbleMine = Color(0xFF0A1B43)      // 내 메시지 배경색
val ChatBubbleOthers = Color(0xFF172A4F)    // 타인 메시지 배경색
val ChatInputBackground = Color(0xFF0D1D3A) // 입력창 배경색

// 연결 강도 관련 색상 (거리 기반)
val ConnectionNear = Color(0xFF21AA73)    // 가까운 거리 (0-100m, 초록색)
val ConnectionMedium = Color(0xFFFFD700)  // 중간 거리 (100-300m, 노란색)
val ConnectionFar = Color(0xFFFF5252)     // 먼 거리 (300m 이상, 빨간색)

// 기존 연결 강도 색상 리네이밍 (호환성 유지)
val ConnectionStrong = ConnectionNear
val ConnectionWeak = ConnectionFar 