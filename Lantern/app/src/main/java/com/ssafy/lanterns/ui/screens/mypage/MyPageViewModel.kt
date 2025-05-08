package com.ssafy.lanterns.ui.screens.mypage

import androidx.annotation.DrawableRes // Drawable 리소스 ID 사용
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.lanterns.R // 리소스 ID 접근
import com.ssafy.lanterns.data.model.User
import com.ssafy.lanterns.data.repository.AuthRepository // AuthRepository 임포트
import com.ssafy.lanterns.data.repository.AuthResult // AuthResult 임포트 추가
import com.ssafy.lanterns.data.repository.UserRepository
// import com.ssafy.lantern.ui.screens.login.LoginViewModel // LoginViewModel 임포트 제거
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log

private const val TAG = "MyPageViewModel"

data class MyPageUiState(
    val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val user: User? = null,
    val errorMessage: String? = null,
    // 입력 상태
    val nicknameInput: String = "",
    // 사용자 이메일
    val email: String = "",
    // 임시로 lantern_image 사용
    @DrawableRes val profileImageResId: Int = R.drawable.lantern_image,
    val availableProfileImages: List<Int> = defaultProfileImages
)

// 사용할 프로필 이미지 리소스 ID 목록 정의
val defaultProfileImages: List<Int> = listOf(
    R.drawable.profile_1, R.drawable.profile_2, R.drawable.profile_3,
    R.drawable.profile_4, R.drawable.profile_5, R.drawable.profile_6,
    R.drawable.profile_7, R.drawable.profile_8, R.drawable.profile_9,
    R.drawable.profile_10, R.drawable.profile_11, R.drawable.profile_12,
    R.drawable.profile_13, R.drawable.profile_14, R.drawable.profile_15
)

@HiltViewModel
class MyPageViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository // LoginViewModel 대신 AuthRepository 주입
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyPageUiState(profileImageResId = R.drawable.lantern_image, availableProfileImages = defaultProfileImages))
    val uiState: StateFlow<MyPageUiState> = _uiState.asStateFlow()

    private val _logoutEvent = MutableSharedFlow<Unit>()
    val logoutEvent: SharedFlow<Unit> = _logoutEvent.asSharedFlow()

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // UserRepository에서 현재 사용자 정보 가져오기
                val currentUser = userRepository.getCurrentUser()
                
                // 사용자 정보가 있으면 UI 상태 업데이트
                if (currentUser != null) {
                    // 이메일 정보도 AuthRepository에서 가져오기
                    val email = authRepository.getCurrentUserEmail() ?: "이메일 정보 없음"
                    Log.d(TAG, "사용자 정보 로드: ${currentUser.nickname}, 이메일: $email")
                    
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            user = currentUser,
                            nicknameInput = currentUser.nickname,
                            email = email,
                            profileImageResId = defaultProfileImages.first(),
                            errorMessage = null
                        )
                    }
                } else {
                    // 이미 로그인되어 있지만 사용자 정보가 없는 경우, AuthRepository에서 정보 가져오기 시도
                    val userId = authRepository.getCurrentUserId()
                    val nickname = authRepository.getCurrentUserNickname()
                    val email = authRepository.getCurrentUserEmail() ?: "이메일 정보 없음"
                    
                    if (userId != null && nickname != null) {
                        // Room DB에 저장할 사용자 객체 생성
                        val user = User(
                            userId = userId,
                            nickname = nickname,
                            deviceId = "" // 구글 로그인에서는 deviceId가 필요 없음
                        )
                        
                        // 사용자 정보를 Room DB에 저장
                        userRepository.saveUser(user)
                        Log.d(TAG, "DB에 사용자 정보 저장: $nickname, 이메일: $email")
                        
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                user = user,
                                nicknameInput = nickname,
                                email = email,
                                profileImageResId = defaultProfileImages.first(),
                                errorMessage = null
                            )
                        }
                    } else {
                        Log.w(TAG, "사용자 정보 없음: userId=$userId, nickname=$nickname")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                user = null,
                                nicknameInput = "",
                                email = "",
                                profileImageResId = defaultProfileImages.first(),
                                errorMessage = "사용자 정보를 불러올 수 없습니다."
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "사용자 정보 로드 오류", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        user = null,
                        nicknameInput = "",
                        email = "",
                        profileImageResId = defaultProfileImages.first(),
                        errorMessage = "오류 발생: ${e.message}"
                    )
                }
            }
        }
    }

    fun toggleEditMode() {
        val currentState = _uiState.value
        if (currentState.isEditing) {
             _uiState.update {
                 it.copy(
                     isEditing = false,
                     nicknameInput = it.user?.nickname ?: "",
                     profileImageResId = defaultProfileImages.first()
                 )
             }
        } else {
            _uiState.update { it.copy(isEditing = true) }
        }
    }

    fun updateNickname(newNickname: String) {
        _uiState.update { it.copy(nicknameInput = newNickname) }
    }

    fun updateProfileImage(@DrawableRes newResId: Int) {
        if (_uiState.value.isEditing) {
            _uiState.update { it.copy(profileImageResId = newResId) }
        }
    }

    fun saveProfileChanges() {
        val currentState = _uiState.value
        val userToUpdate = currentState.user ?: run {
             _uiState.update { it.copy(errorMessage = "사용자 정보가 없어 저장할 수 없습니다.") }
             return
        }

        val updatedUser = userToUpdate.copy(
            nickname = currentState.nicknameInput
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // 실제 사용자 정보 업데이트
                userRepository.updateUser(updatedUser)
                Log.d(TAG, "프로필 정보 업데이트: ${updatedUser.nickname}")

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isEditing = false,
                        user = updatedUser,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "프로필 저장 오류", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = "저장 실패: ${e.message}") }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = authRepository.signOut()
            when (result) {
                is AuthResult.Success -> {
                    Log.d(TAG, "로그아웃 성공")
                    _logoutEvent.emit(Unit)
                }
                is AuthResult.Error -> {
                    Log.e(TAG, "로그아웃 실패: ${result.message}")
                    _uiState.update { it.copy(isLoading = false, errorMessage = "로그아웃 실패: ${result.message}") }
                }
                AuthResult.Loading -> {
                    // Loading 상태 처리
                }
            }
        }
    }
} 