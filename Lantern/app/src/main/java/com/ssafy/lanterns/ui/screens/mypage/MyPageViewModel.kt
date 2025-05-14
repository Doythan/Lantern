package com.ssafy.lanterns.ui.screens.mypage

import androidx.annotation.DrawableRes // Drawable 리소스 ID 사용
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.lanterns.R // 리소스 ID 접근
import com.ssafy.lanterns.data.model.User
import com.ssafy.lanterns.data.repository.AuthRepository // AuthRepository 임포트
import com.ssafy.lanterns.data.repository.AuthResult // AuthResult 임포트 추가
import com.ssafy.lanterns.data.repository.UserRepository
// import com.ssafy.lanterns.ui.util.ImageUtils // 전체 임포트 제거
import com.ssafy.lanterns.ui.util.getAllProfileImageResources // 개별 함수 임포트
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
    // 선택된 프로필 이미지의 "번호" (1-15), 기본값 1
    val selectedProfileImageNumber: Int = 1, // User 객체의 값을 따라가거나, 기본값 1
    // 프로필 이미지 선택 다이얼로그에 표시할 이미지 정보 Map<Int, Int> : 번호 to 리소스ID
    val availableProfileImageResources: Map<Int, Int> = getAllProfileImageResources() // 직접 함수 호출
)

// 사용할 프로필 이미지 리소스 ID 목록 정의 -> ImageUtils.getAllProfileImageResources()로 대체 가능하므로 제거 또는 주석 처리
/*
val defaultProfileImages: List<Int> = listOf(
    R.drawable.profile_1, R.drawable.profile_2, R.drawable.profile_3,
    R.drawable.profile_4, R.drawable.profile_5, R.drawable.profile_6,
    R.drawable.profile_7, R.drawable.profile_8, R.drawable.profile_9,
    R.drawable.profile_10, R.drawable.profile_11, R.drawable.profile_12,
    R.drawable.profile_13, R.drawable.profile_14, R.drawable.profile_15
)
*/

@HiltViewModel
class MyPageViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyPageUiState()) // 기본값으로 UiState 생성
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
                val currentUser = userRepository.getCurrentUser()
                if (currentUser != null) {
                    val email = authRepository.getCurrentUserEmail() ?: "이메일 정보 없음"
                    Log.d(TAG, "사용자 정보 로드: ${currentUser.nickname}, 이미지 번호: ${currentUser.selectedProfileImageNumber}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            user = currentUser,
                            nicknameInput = currentUser.nickname,
                            email = email,
                            selectedProfileImageNumber = currentUser.selectedProfileImageNumber, // DB 값으로 설정
                            errorMessage = null
                        )
                    }
                } else {
                    val userId = authRepository.getCurrentUserId()
                    val nickname = authRepository.getCurrentUserNickname()
                    val email = authRepository.getCurrentUserEmail() ?: "이메일 정보 없음"
                    if (userId != null && nickname != null) {
                        val user = User(
                            userId = userId,
                            nickname = nickname,
                            deviceId = "", // 구글 로그인에서는 deviceId가 필요 없음
                            // selectedProfileImageNumber는 User의 기본값(1) 사용
                        )
                        userRepository.saveUser(user)
                        Log.d(TAG, "DB에 사용자 정보 저장: $nickname, 이미지 번호: ${user.selectedProfileImageNumber}")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                user = user,
                                nicknameInput = nickname,
                                email = email,
                                selectedProfileImageNumber = user.selectedProfileImageNumber, // 저장된 User 객체의 값 사용
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
                                selectedProfileImageNumber = 1, // 기본값
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
                        selectedProfileImageNumber = 1, // 기본값
                        errorMessage = "오류 발생: ${e.message}"
                    )
                }
            }
        }
    }

    fun toggleEditMode() {
        val currentState = _uiState.value
        if (currentState.isEditing) {
             // 편집 모드 종료 시, User 객체의 실제 이미지 번호로 복원
             _uiState.update {
                 it.copy(
                     isEditing = false,
                     nicknameInput = it.user?.nickname ?: "",
                     selectedProfileImageNumber = it.user?.selectedProfileImageNumber ?: 1
                 )
             }
        } else {
            _uiState.update { it.copy(isEditing = true) }
        }
    }

    fun updateNickname(newNickname: String) {
        _uiState.update { it.copy(nicknameInput = newNickname) }
    }

    // 선택된 프로필 이미지의 "번호"를 업데이트합니다.
    fun updateSelectedProfileImageNumber(newImageNumber: Int) {
        if (_uiState.value.isEditing) {
            // 유효한 이미지 번호인지 확인 (1-15)
            if (newImageNumber in 1..15) {
                _uiState.update { it.copy(selectedProfileImageNumber = newImageNumber) }
            }
        }
    }

    fun saveProfileChanges() {
        val currentState = _uiState.value
        val userToUpdate = currentState.user ?: run {
             _uiState.update { it.copy(errorMessage = "사용자 정보가 없어 저장할 수 없습니다.") }
             return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            var success = true
            var finalErrorMessage: String? = null

            try {
                // 1. 닉네임 변경 사항이 있다면 업데이트
                if (userToUpdate.nickname != currentState.nicknameInput) {
                    userRepository.updateNickname(userToUpdate.userId, currentState.nicknameInput)
                    Log.d(TAG, "닉네임 업데이트: ${currentState.nicknameInput}")
                }

                // 2. 프로필 이미지 번호 변경 사항이 있다면 업데이트
                if (userToUpdate.selectedProfileImageNumber != currentState.selectedProfileImageNumber) {
                    userRepository.updateProfileImageNumber(userToUpdate.userId, currentState.selectedProfileImageNumber)
                    Log.d(TAG, "프로필 이미지 번호 업데이트: ${currentState.selectedProfileImageNumber}")
                }
                
                // 업데이트된 사용자 정보 다시 로드하여 UI 반영
                val updatedUser = userRepository.getUserById(userToUpdate.userId)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isEditing = false,
                        user = updatedUser, // DB에서 최신 정보 가져와서 반영
                        nicknameInput = updatedUser?.nickname ?: "",
                        selectedProfileImageNumber = updatedUser?.selectedProfileImageNumber ?: 1,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "프로필 저장 오류", e)
                success = false
                finalErrorMessage = "저장 실패: ${e.message}"
                _uiState.update { it.copy(isLoading = false, errorMessage = finalErrorMessage) }
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