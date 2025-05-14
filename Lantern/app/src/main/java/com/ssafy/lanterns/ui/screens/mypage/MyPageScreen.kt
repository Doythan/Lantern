package com.ssafy.lanterns.ui.screens.mypage

import android.widget.Toast // 토스트 메시지용
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.ssafy.lanterns.R
import com.ssafy.lanterns.data.model.User
import com.ssafy.lanterns.ui.theme.*
import com.ssafy.lanterns.ui.util.getProfileImageByNumber
import com.ssafy.lanterns.ui.util.getAllProfileImageResources
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.material.ContentAlpha
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb

@Composable
fun MyPageScreen(
    viewModel: MyPageViewModel = hiltViewModel(),
    onNavigateToLogin: () -> Unit,
    paddingValues: PaddingValues // MainScaffold로부터 전달받는 패딩
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showImageSelectionDialog by remember { mutableStateOf(false) }

    // 로그아웃 이벤트 감지
    LaunchedEffect(key1 = viewModel.logoutEvent) {
        viewModel.logoutEvent.collectLatest {
            Toast.makeText(context, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
            onNavigateToLogin()
        }
    }

    // 에러 메시지 표시
    LaunchedEffect(key1 = uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // 이미지 선택 다이얼로그
    if (showImageSelectionDialog) {
        ProfileImageSelectionDialog(
            availableImageResources = uiState.availableProfileImageResources,
            onDismiss = { showImageSelectionDialog = false },
            onImageSelected = { selectedNumber ->
                viewModel.updateSelectedProfileImageNumber(selectedNumber)
                showImageSelectionDialog = false
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(NavyTop, NavyBottom)
                )
            )
            .padding(paddingValues) // 하단 네비게이션 바 고려
    ) {
        // Content Area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 상단 헤더
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "프로필",
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.25).sp
                )
                
                Button(
                    onClick = {
                        if (uiState.isEditing) viewModel.saveProfileChanges()
                        else viewModel.toggleEditMode()
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (uiState.isEditing) Color(0xFF1DE9B6) else Color(0xFF1DE9B6).copy(alpha = 0.8f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.height(40.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (uiState.isEditing) "완료" else "수정",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Profile Image Section
            Box(contentAlignment = Alignment.Center) {
                val avatarSize = 180.dp
                val subtleBorderThickness = 1.dp

                Box(
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colors.surface.copy(alpha = 0.1f))
                        .border(
                            width = subtleBorderThickness,
                            color = Color.White.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                ) {
                    val actualImageResId = getProfileImageByNumber(uiState.selectedProfileImageNumber)
                    Image(
                        painter = painterResource(id = actualImageResId),
                        contentDescription = "Profile Image",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .padding(subtleBorderThickness)
                            .clickable(enabled = uiState.isEditing) {
                                if (uiState.isEditing) {
                                    showImageSelectionDialog = true
                                }
                            },
                        contentScale = ContentScale.Crop
                    )
                }

                if (uiState.isEditing) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1DE9B6))
                            .align(Alignment.BottomEnd)
                            .offset(x = 8.dp, y = 8.dp)
                            .clickable { showImageSelectionDialog = true }
                            .zIndex(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile Image",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Nickname Field
            ProfileEditField(
                label = "닉네임",
                value = uiState.nicknameInput,
                onValueChange = viewModel::updateNickname,
                isEditing = uiState.isEditing,
                labelColor = Color(0xB3FFFFFF),
                valueColor = Color(0xE6FFFFFF),
                underlineIdleColor = Color(0x26FFFFFF),
                underlineFocusedColor = Color(0xE6FFFFFF)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Email Field
            ProfileDisplayField(
                label = "이메일",
                value = uiState.email,
                labelColor = Color(0xB3FFFFFF),
                valueColor = Color(0xE6FFFFFF)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Log Out Button
            val logoutButtonInteractionSource = remember { MutableInteractionSource() }
            val isLogoutButtonPressed by logoutButtonInteractionSource.collectIsPressedAsState()
            val logoutButtonColor = Color(0xFF1DE9B6)

            Button(
                onClick = { viewModel.logout() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (isLogoutButtonPressed) logoutButtonColor.copy(alpha = 0.8f) else logoutButtonColor
                ),
                interactionSource = logoutButtonInteractionSource
            ) {
                Text(
                    text = "로그아웃",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 로딩 인디케이터
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colors.primary)
            }
        }
    }
}

@Composable
fun ProfileEditField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isEditing: Boolean,
    labelColor: Color = Color.White.copy(alpha = ContentAlpha.medium),
    valueColor: Color = Color.White,
    underlineIdleColor: Color = Color.White.copy(alpha = 0.3f),
    underlineFocusedColor: Color = Color.White
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = isEditing,
            textStyle = TextStyle(
                color = if (isEditing) valueColor else valueColor.copy(alpha = ContentAlpha.disabled),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            ),
            cursorBrush = SolidColor(valueColor),
            modifier = Modifier.fillMaxWidth(),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Box {
                    innerTextField()
                    if (value.isEmpty() && isEditing) {
                        Text(
                            text = "닉네임을 입력하세요",
                            color = valueColor.copy(alpha = ContentAlpha.medium),
                            fontSize = 18.sp
                        )
                    }
                }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Divider(
            color = if (isFocused && isEditing) underlineFocusedColor else underlineIdleColor,
            thickness = if (isFocused && isEditing) 2.dp else 1.dp
        )
    }
}

@Composable
fun ProfileDisplayField(
    label: String,
    value: String,
    labelColor: Color = Color.White.copy(alpha = ContentAlpha.medium),
    valueColor: Color = Color.White
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Divider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp)
    }
}

@Composable
fun ProfileImageSelectionDialog(
    availableImageResources: Map<Int, Int>,
    onDismiss: () -> Unit,
    onImageSelected: (Int) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(300)) + 
                   slideInVertically(
                       animationSpec = tween(300, easing = FastOutSlowInEasing),
                       initialOffsetY = { it / 2 }
                   ),
            exit = fadeOut(animationSpec = tween(300)) +
                   slideOutVertically(
                       animationSpec = tween(300, easing = FastOutSlowInEasing),
                       targetOffsetY = { it / 2 }
                   )
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = NavyTop.copy(alpha = 0.95f),
                border = BorderStroke(
                    width = 1.dp,
                    color = Color(0xFF1DE9B6)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "프로필 이미지 선택",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )
                    
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .heightIn(max = 300.dp)
                            .padding(8.dp)
                    ) {
                        items(availableImageResources.entries.toList()) { entry ->
                            val number = entry.key
                            val imageResId = entry.value
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .border(
                                        BorderStroke(
                                            2.dp,
                                            Color(0xFF1DE9B6)
                                        ),
                                        CircleShape
                                    )
                                    .clickable { onImageSelected(number) },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = imageResId),
                                    contentDescription = "Profile Option $number",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF1DE9B6)
                        )
                    ) {
                        Text(
                            "취소",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFF)
@Composable
fun MyPageScreenPreview() {
    LanternTheme {
         val previewUser = User(
             userId = 0L, 
             nickname = "@previewUser",
             deviceId = "preview_device_id",
             selectedProfileImageNumber = 1
         )
         val previewUiState = MyPageUiState(
             user = previewUser,
             nicknameInput = "@previewUser",
             selectedProfileImageNumber = 1,
             availableProfileImageResources = getAllProfileImageResources()
         )
         val isEditing = remember { mutableStateOf(false) }
         var currentImageNumber by remember { mutableStateOf(previewUiState.selectedProfileImageNumber) }
         var currentNickname by remember { mutableStateOf(previewUiState.nicknameInput)}
         var showDialog by remember { mutableStateOf(false) }

        if(showDialog) {
             ProfileImageSelectionDialog(
                 availableImageResources = previewUiState.availableProfileImageResources,
                 onDismiss = { showDialog = false },
                 onImageSelected = { selectedNumber -> 
                     currentImageNumber = selectedNumber
                     showDialog = false
                 }
             )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(NavyTop, NavyBottom)
                    )
                )
        ) {
             Column(
                 modifier = Modifier
                     .fillMaxSize()
                     .padding(horizontal = 32.dp, vertical = 24.dp),
                 horizontalAlignment = Alignment.CenterHorizontally
             ) {
                 // 상단 헤더
                 Row(
                     modifier = Modifier
                         .fillMaxWidth()
                         .padding(bottom = 24.dp),
                     horizontalArrangement = Arrangement.SpaceBetween,
                     verticalAlignment = Alignment.CenterVertically
                 ) {
                     Text(
                         text = "프로필",
                         color = Color.White,
                         fontSize = 25.sp,
                         fontWeight = FontWeight.Bold,
                         letterSpacing = (-0.25).sp
                     )
                     
                     Button(
                         onClick = { isEditing.value = !isEditing.value },
                         colors = ButtonDefaults.buttonColors(
                             backgroundColor = if (isEditing.value) MaterialTheme.colors.primary 
                                 else MaterialTheme.colors.primaryVariant.copy(alpha = 0.7f),
                             contentColor = Color.White
                         ),
                         shape = RoundedCornerShape(20.dp),
                         modifier = Modifier.height(40.dp)
                     ) {
                         Text(
                             text = if (isEditing.value) "완료" else "수정",
                             fontSize = 16.sp,
                             fontWeight = FontWeight.Bold
                         )
                     }
                 }
                 
                 Spacer(modifier = Modifier.height(32.dp))
                 
                 // 프로필 이미지
                 Box(contentAlignment = Alignment.Center) {
                     Box(
                         modifier = Modifier
                             .size(160.dp)
                             .clip(CircleShape)
                             .background(MaterialTheme.colors.surface.copy(alpha = 0.2f))
                             .border(
                                 width = 2.dp,
                                 brush = Brush.linearGradient(
                                     colors = listOf(MaterialTheme.colors.primary, MaterialTheme.colors.primaryVariant)
                                 ),
                                 shape = CircleShape
                             )
                     ) {
                         val actualImageResId = getProfileImageByNumber(currentImageNumber)
                         Image(
                             painter = painterResource(id = actualImageResId),
                             contentDescription = "Profile Image",
                             modifier = Modifier
                                 .fillMaxSize()
                                 .clip(CircleShape)
                                 .clickable(enabled = isEditing.value) { 
                                     if(isEditing.value) showDialog = true 
                                 },
                             contentScale = ContentScale.Crop
                         )
                     }
                     
                     if (isEditing.value) {
                         Box(
                             modifier = Modifier
                                 .size(50.dp)
                                 .clip(CircleShape)
                                 .background(Color(0xFF1DE9B6))
                                 .align(Alignment.BottomEnd)
                                 .offset(x = 8.dp, y = 8.dp)
                                 .clickable { showDialog = true }
                                 .zIndex(1f),
                             contentAlignment = Alignment.Center
                         ) {
                             Icon(
                                 imageVector = Icons.Default.Edit,
                                 contentDescription = "Edit Profile Image",
                                 tint = Color.White,
                                 modifier = Modifier.size(24.dp)
                             )
                         }
                     }
                 }
                 
                 Spacer(modifier = Modifier.height(48.dp))
                 
                 // 닉네임 필드
                 ProfileEditField(
                     label = "닉네임",
                     value = currentNickname,
                     onValueChange = { currentNickname = it },
                     isEditing = isEditing.value
                 )
                 
                 Spacer(modifier = Modifier.height(24.dp))
                 
                 // 이메일 필드
                 ProfileDisplayField(
                     label = "이메일",
                     value = "이메일 정보 없음 (임시)"
                 )
                 
                 Spacer(modifier = Modifier.weight(1f))
                 
                 // 로그아웃 버튼
                 Button(
                     onClick = { },
                     modifier = Modifier
                         .fillMaxWidth()
                         .height(60.dp),
                     shape = RoundedCornerShape(20.dp),
                     colors = ButtonDefaults.buttonColors(
                         backgroundColor = MaterialTheme.colors.primary
                     )
                 ) {
                     Text(
                         text = "로그아웃",
                         fontSize = 20.sp,
                         fontWeight = FontWeight.SemiBold,
                         color = Color.White
                     )
                 }
             }
        }
    }
}