package com.ssafy.lanterns.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.lanterns.ui.theme.BleBlue1
import com.ssafy.lanterns.ui.util.getProfileImageResId
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import com.ssafy.lanterns.ui.theme.LanternTheme
import com.ssafy.lanterns.ui.theme.ConnectionNear

/**
 * 앱 전체에서 사용될 수 있는 공통 검색 바
 */
@Composable
fun CommonSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholderText: String = "검색",
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholderText, color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search Icon", tint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)) },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp) // 패딩 조정 (기존 FriendListScreen 기준)
            .height(48.dp),
        shape = RoundedCornerShape(50), // 둥근 모서리
        colors = TextFieldDefaults.outlinedTextFieldColors(
            backgroundColor = Color(0xFF232323), // FriendListScreen과 통일된 배경색
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = Color.Transparent,
            textColor = MaterialTheme.colors.onSurface,
            cursorColor = MaterialTheme.colors.primary,
            leadingIconColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
            placeholderColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
        ),
        singleLine = true
    )
}

/**
 * 공통 프로필 아바타 컴포넌트
 * 
 * @param profileId 프로필 이미지 ID (getProfileImageResId 함수와 함께 사용)
 * @param size 아바타 크기 (기본 48dp)
 * @param modifier 추가 모디파이어 
 * @param borderColor 경계선 색상 (hasBorder가 true일 때 사용)
 * @param hasBorder 경계선 표시 여부
 * @param onClick 클릭 이벤트 처리 (선택 사항)
 */
@Composable
fun ProfileAvatar(
    profileId: Int,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
    borderColor: Color = Color.Transparent,
    hasBorder: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    if (hasBorder) {
        // 경계선이 있는 프로필 아바타
        androidx.compose.foundation.layout.Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            borderColor,
                            BleBlue1
                        )
                    )
                )
                .padding(2.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        ) {
            Image(
                painter = painterResource(id = getProfileImageResId(profileId)),
                contentDescription = "Profile Avatar",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
    } else {
        // 경계선이 없는 기본 프로필 아바타
        Image(
            painter = painterResource(id = getProfileImageResId(profileId)),
            contentDescription = "Profile Avatar",
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colors.surface)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            contentScale = ContentScale.Crop
        )
    }
}

@Preview
@Composable
fun ProfileAvatarPreview() {
    LanternTheme {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .background(Color(0xFF051225))
        ) {
            // 기본 프로필 아바타
            ProfileAvatar(
                profileId = 1,
                size = 48.dp
            )
            
            // 경계선이 있는 프로필 아바타
            ProfileAvatar(
                profileId = 2,
                size = 48.dp,
                borderColor = ConnectionNear,
                hasBorder = true,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
} 