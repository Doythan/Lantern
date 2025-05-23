package com.ssafy.lanterns.ui.screens.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ssafy.lanterns.ui.theme.AppBackground
import com.ssafy.lanterns.ui.theme.BleDarkBlue
import com.ssafy.lanterns.ui.theme.BleAccent
import com.ssafy.lanterns.ui.theme.LanternYellow
import com.ssafy.lanterns.ui.theme.LanternCoreYellow
import com.ssafy.lanterns.ui.theme.LanternGlowOrange
import com.ssafy.lanterns.ui.theme.NavyMedium
import com.ssafy.lanterns.ui.theme.SurfaceDark
import com.ssafy.lanterns.ui.theme.TextPrimary
import com.ssafy.lanterns.ui.theme.TextSecondary
import com.ssafy.lanterns.ui.util.getProfileImageByNumber
import kotlin.random.Random

/**
 * 프로필 모달 컴포넌트
 * 
 * @param person 모달에 표시할 사용자 정보
 * @param onDismiss 모달 닫기 콜백
 * @param onCallClick 통화 버튼 클릭 콜백
 * @param onChatClick 채팅 버튼 클릭 콜백
 */
@Composable
fun ProfileModal(
    person: NearbyPerson,
    onDismiss: () -> Unit,
    onCallClick: () -> Unit,
    onChatClick: () -> Unit
) {
    // 프로필 이미지 번호 결정 로직 개선
    // BLE로 수신된 프로필 이미지 번호가 있으면 그것을 사용, 없으면 기존 로직 유지
    val imageNumber = if (person.profileImageNumber > 0) {
        // BLE에서 수신된 프로필 이미지 번호 사용
        person.profileImageNumber
    } else {
        // 기존 로직 유지: ID 기반 랜덤 생성 (1-6)
        try {
            val userId = person.serverUserIdString.toLong()
            (userId % 6 + 1).toInt()
        } catch (e: Exception) {
            1 // 기본값
        }
    }
    
    // Dialog 표시
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // 반투명 배경 오버레이 (우주 느낌의 블러 효과 추가)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.8f)
                    .background(AppBackground)
                    .blur(20.dp)
                    .clickable { onDismiss() }
            )
            
            // 모달 컨텐츠 - 페이드인 애니메이션
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(200))
            ) {
                // 모달 카드 (가로로 넓은 형태)
                Card(
                    modifier = Modifier
                        .width(320.dp) // 가로 넓게 조정
                        .height(200.dp) // 세로 높이 제한
                        .shadow(elevation = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent // 배경색 투명으로 설정
                    )
                ) {
                    // 그라데이션 배경 적용
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        SurfaceDark.copy(alpha = 0.9f),
                                        BleDarkBlue.copy(alpha = 0.8f)
                                    )
                                )
                            )
                    ) {
                        // X 버튼 (닫기)
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "닫기",
                                tint = TextSecondary
                            )
                        }
                        
                        // 가로 레이아웃으로 변경 (프로필 이미지 좌측, 정보 우측)
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 프로필 이미지 (랜턴 느낌의 황금색 테두리 추가)
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // 랜턴 느낌의 빛나는 배경 효과
                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .background(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    LanternCoreYellow.copy(alpha = 0.4f),
                                                    LanternGlowOrange.copy(alpha = 0.2f),
                                                    Color.Transparent
                                                )
                                            ),
                                            shape = CircleShape
                                        )
                                )
                                
                                Image(
                                    painter = painterResource(id = getProfileImageByNumber(imageNumber)),
                                    contentDescription = "프로필 이미지",
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = 2.dp,
                                            brush = Brush.linearGradient(
                                                colors = listOf(
                                                    LanternYellow,
                                                    LanternGlowOrange
                                                )
                                            ),
                                            shape = CircleShape
                                        ),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            
                            // 정보 및 버튼 영역
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, end = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // 사용자 이름 (닉네임)
                                Text(
                                    text = person.nickname,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                
                                // 신호 강도 표시
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val signalText = when(person.signalLevel) {
                                        3 -> "강함"
                                        2 -> "중간"
                                        else -> "약함"
                                    }
                                    val signalColor = when(person.signalLevel) {
                                        3 -> BleAccent
                                        2 -> LanternYellow
                                        else -> Color.Red
                                    }
                                    
                                    Text(
                                        text = "신호 강도: ",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = signalText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = signalColor
                                    )
                                }
                                
                                // 액션 버튼 (통화, 채팅)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    // 통화 버튼
                                    ActionButton(
                                        icon = Icons.Default.Call,
                                        backgroundColor = BleAccent,
                                        onClick = onCallClick
                                    )
                                    
                                    // 채팅 버튼
                                    ActionButton(
                                        icon = Icons.Default.Chat,
                                        backgroundColor = LanternGlowOrange,
                                        onClick = onChatClick
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 원형 액션 버튼 컴포넌트
 */
@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(50.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .clickable { onClick() },
        color = backgroundColor
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
} 