package com.ssafy.lanterns.ui.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.lanterns.ui.theme.BrightErrorRed

@Composable
fun RescueAlertNotification(
    message: String,
    onDismiss: () -> Unit, // 닫기 버튼용 콜백
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = BrightErrorRed) // 테마의 에러 색상 사용
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            // horizontalArrangement = Arrangement.SpaceBetween
        ) {

            // 위험 아이콘 추가
            Icon(
                imageVector = Icons.Filled.Warning, // Material 아이콘 사용 (다른 아이콘으로 대체 가능)
                contentDescription = "위험 알림 아이콘",
                tint = MaterialTheme.colorScheme.onErrorContainer, // 아이콘 색상
                modifier = Modifier.size(28.dp) // 아이콘 크기 (텍스트보다 약간 크게)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), // 또는 titleSmall
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f) // 텍스트가 남은 공간을 채우도록
            )
            // 닫기 버튼 (선택 사항)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "구조 알림 닫기",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}