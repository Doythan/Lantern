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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
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
import com.ssafy.lanterns.ui.theme.LanternYellow
import com.ssafy.lanterns.utils.getConnectionColorBySignalLevel
import com.ssafy.lanterns.utils.getConnectionStrengthTextFromSignalLevel

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
    onPersonClick: (serverUserIdString: String) -> Unit,
    onCallClick: (serverUserIdString: String) -> Unit
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
                    .background(MaterialTheme.colorScheme.scrim)
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
                        containerColor = MaterialTheme.colorScheme.surface
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
                                text = "주변에 탐지된 랜턴 (${people.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "닫기",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                        
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        
                        // 사람 목록 - Depth와 신호 강도 기준으로 정렬
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(
                                // 정렬 기준: 1) Depth가 낮은 순(가까운 순), 2) 신호 강도가 높은 순, 3) RSSI가 높은 순
                                people.sortedWith(
                                    compareBy<NearbyPerson> { it.calculatedVisualDepth }
                                        .thenByDescending { it.signalLevel }
                                        .thenByDescending { it.rssi }
                                )
                            ) { person ->
                                PersonListItemWithButtons(
                                    person = person,
                                    onChatClick = { onPersonClick(person.serverUserIdString) },
                                    onCallClick = { onCallClick(person.serverUserIdString) }
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
 * 주변 사람 목록 아이템 (채팅 및 통화 버튼 포함)
 */
@Composable
fun PersonListItemWithButtons(
    person: NearbyPerson,
    onChatClick: () -> Unit,
    onCallClick: () -> Unit
) {
    val connectionColor = getConnectionColorBySignalLevel(person.signalLevel)
    val connectionText = getConnectionStrengthTextFromSignalLevel(person.signalLevel)
    
    // 통화 버튼은 신호 강도가 가장 강한 경우(Level 3)에만 활성화
    val isCallEnabled = person.signalLevel >= 3
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 왼쪽: 프로필 이미지와 사용자 정보
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 아바타 이미지 - 신호 강도에 따라 테두리 색상 변경
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
                        .border(width = 2.dp, color = connectionColor.copy(alpha = 0.7f), shape = CircleShape),
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
                
                // 사용자 정보
                Column {
                    Text(
                        text = person.nickname, // nickname 사용
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // 연결 정보 (홉수 및 신호 강도)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 홉수 표시
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${person.calculatedVisualDepth}홉",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        
                        // 신호 강도 표시
                        Box(
                            modifier = Modifier
                                .background(
                                    color = connectionColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = connectionText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = connectionColor
                            )
                        }
                    }
                }
            }
            
            // 오른쪽: 채팅 및 통화 버튼
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 채팅 버튼
                IconButton(
                    onClick = onChatClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            color = LanternYellow
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "채팅하기",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                
                // 통화 버튼 (신호 강도 Level 3일 때만 활성화)
                IconButton(
                    onClick = onCallClick,
                    enabled = isCallEnabled,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCallEnabled) BleAccent 
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "전화걸기",
                        tint = if (isCallEnabled) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
} 