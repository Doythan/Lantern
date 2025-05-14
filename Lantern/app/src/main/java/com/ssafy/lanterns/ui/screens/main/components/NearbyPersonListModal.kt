package com.ssafy.lanterns.ui.screens.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ssafy.lanterns.ui.theme.BleAccent
import com.ssafy.lanterns.ui.theme.BleBlue1
import com.ssafy.lanterns.ui.theme.BleBlue2
import com.ssafy.lanterns.ui.theme.ConnectionFar
import com.ssafy.lanterns.ui.theme.ConnectionMedium
import com.ssafy.lanterns.ui.theme.ConnectionNear
import com.ssafy.lanterns.ui.theme.TextWhite
import com.ssafy.lanterns.ui.theme.TextWhite70
import com.ssafy.lanterns.utils.getConnectionColorByDistance
import com.ssafy.lanterns.utils.getConnectionStrengthText
import kotlin.math.roundToInt

// 이징 애니메이션 커브 (모달 애니메이션용)
private val EaseOutQuart = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)
private val EaseInQuad = CubicBezierEasing(0.55f, 0.085f, 0.68f, 0.53f)

/**
 * 주변 사람 목록 모달
 */
@Composable
fun NearbyPersonListModal(
    people: List<NearbyPerson>,
    onDismiss: () -> Unit,
    onPersonClick: (userId: String) -> Unit
) {
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
            contentAlignment = Alignment.BottomCenter
        ) {
            // 배경 어둡게 처리 (탭해서 닫기 가능)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.5f)
                    .background(Color.Black)
                    .clickable { onDismiss() }
            )
            
            // 모달 콘텐츠 - 아래에서 위로 슬라이드 애니메이션
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300, easing = EaseOutQuart)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(200, easing = EaseInQuad)
                )
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF0F1D3A)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // 헤더 영역
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "주변에 탐지된 사람 (${people.size})",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "닫기",
                                    tint = TextWhite70
                                )
                            }
                        }
                        
                        Divider(color = Color.White.copy(alpha = 0.1f))
                        
                        // 사람 목록
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(people.sortedBy { it.distance }) { person ->
                                PersonListItem(
                                    person = person,
                                    onClick = { onPersonClick(person.userId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 주변 사람 목록 아이템
 */
@Composable
fun PersonListItem(
    person: NearbyPerson,
    onClick: () -> Unit
) {
    val connectionColor = getConnectionColorByDistance(person.distance)
    
    // signalStrength 값에 따라 텍스트 및 색상 결정
    val signalStrengthText = when {
        person.signalStrength >= 0.7f -> "강함"
        person.signalStrength >= 0.4f -> "중간"
        else -> "약함"
    }
    val signalStrengthColor = when {
        person.signalStrength >= 0.7f -> ConnectionNear
        person.signalStrength >= 0.4f -> ConnectionMedium
        else -> ConnectionFar
    }
    
    // 신호 강도에 따른 배경색 그라데이션
    val backgroundGradient = when {
        person.signalStrength >= 0.7f -> Brush.linearGradient(
            colors = listOf(
                Color(0xFF1A3468).copy(alpha = 0.7f),
                Color(0xFF0D6166).copy(alpha = 0.7f)
            )
        )
        person.signalStrength >= 0.4f -> Brush.linearGradient(
            colors = listOf(
                Color(0xFF1A3468).copy(alpha = 0.6f),
                Color(0xFF384C6D).copy(alpha = 0.6f)
            )
        )
        else -> Brush.linearGradient(
            colors = listOf(
                Color(0xFF1A2643).copy(alpha = 0.7f),
                Color(0xFF262640).copy(alpha = 0.5f)
            )
        )
    }
    
    // 신호 강도에 따른 테두리 색상
    val borderColor = when {
        person.signalStrength >= 0.7f -> ConnectionNear.copy(alpha = 0.5f)
        person.signalStrength >= 0.4f -> ConnectionMedium.copy(alpha = 0.4f)
        else -> ConnectionFar.copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .background(backgroundGradient)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 아바타 이미지
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    BleBlue1,
                                    BleBlue2
                                )
                            )
                        )
                        .border(width = 2.dp, color = borderColor, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = rememberVectorPainter(Icons.Default.Person),
                        contentDescription = "프로필 이미지",
                        modifier = Modifier
                            .size(30.dp)
                            .alpha(0.9f),
                    )
                }
                
                // 사람 정보
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = person.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "신호: ",
                            fontSize = 12.sp,
                            color = TextWhite70
                        )
                        Text(
                            text = signalStrengthText,
                            fontSize = 12.sp,
                            color = signalStrengthColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // 거리 표시
                Box(
                    modifier = Modifier
                        .background(
                            color = connectionColor.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${person.distance.toInt()} m",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = connectionColor
                    )
                }
            }
        }
    }
} 