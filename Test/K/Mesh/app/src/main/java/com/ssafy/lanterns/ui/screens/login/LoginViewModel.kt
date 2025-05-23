package com.ssafy.lanterns.ui.screens.login // 패키지 경로는 실제 프로젝트 구조에 맞게 조정하세요

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.ssafy.lanterns.R
import com.ssafy.lanterns.data.model.User
import com.ssafy.lanterns.data.repository.AuthRepository
import com.ssafy.lanterns.data.repository.AuthResult
import com.ssafy.lanterns.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "LoginViewModel"

// 로그인 상태를 나타내는 Sealed Interface
sealed interface LoginUiState {
    object Idle : LoginUiState
    object Loading : LoginUiState
    data class Success(val user: User) : LoginUiState
    data class Error(val message: String) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // 초기화 시 로그인 상태 확인 (앱 시작 시 자동 로그인)
    init {
        checkLoginStatus()
    }

    // 로그인 상태 확인
    private fun checkLoginStatus() {
        viewModelScope.launch {
            if (authRepository.isLoggedIn()) {
                val userId = authRepository.getCurrentUserId() ?: return@launch
                val nickname = authRepository.getCurrentUserNickname() ?: return@launch
                
                val user = User(
                    userId = userId,
                    nickname = nickname,
                    deviceId = ""
                )
                
                _uiState.update { LoginUiState.Success(user) }
                Log.d(TAG, "자동 로그인 성공: ${user.nickname}")
            }
        }
    }

    // 서버 클라이언트 ID
    private val serverClientId: String by lazy {
        applicationContext.getString(R.string.server_client_id)
    }

    // GoogleSignInClient 인스턴스 (지연 초기화)
    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(serverClientId)
            .requestEmail()
            .requestProfile()
            .build()
        
        GoogleSignIn.getClient(applicationContext, gso)
    }

    /**
     * Google 로그인 인텐트를 반환합니다.
     */
    fun getSignInIntent(): Intent {
        // 항상 새로운 구글 로그인 세션을 시작하도록 기존 세션 로그아웃
        googleSignInClient.signOut().addOnFailureListener { e ->
            Log.e(TAG, "Google 로그아웃 실패", e)
        }
        
        return googleSignInClient.signInIntent
    }

    /**
     * ActivityResult 로부터 받은 로그인 결과를 처리합니다.
     */
    fun handleSignInResult(data: Intent?) {
        _uiState.update { LoginUiState.Loading }
        
        if (data == null) {
            Log.e(TAG, "로그인 결과 Intent가 null입니다")
            _uiState.update { LoginUiState.Error("로그인 처리 중 오류가 발생했습니다.") }
            return
        }
        
        val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
        
        try {
            val account = task.getResult(ApiException::class.java)
            Log.i(TAG, "구글 로그인 성공: ${account.email}, 이름=${account.displayName}")
            
            // ID 토큰 가져오기
            val idToken = account.idToken
            if (idToken != null) {
                // ID 토큰으로 백엔드에 인증 시도
                googleLoginWithIdToken(idToken, account)
            } else {
                Log.e(TAG, "ID 토큰이 null입니다")
                _uiState.update { LoginUiState.Error("구글 로그인 정보를 가져오지 못했습니다.") }
            }
        } catch (e: ApiException) {
            // 로그인 실패 처리
            val statusCode = e.statusCode
            Log.e(TAG, "Google SignIn API 예외: code=$statusCode", e)
            
            val errorMessage = when (e.statusCode) {
                10 -> "앱이 Google에 등록되지 않았거나 설정 오류입니다." // DEVELOPER_ERROR
                16 -> "앱에 대한 적절한 인증 설정이 없습니다." // INTERNAL_ERROR
                7 -> "네트워크 오류가 발생했습니다." // NETWORK_ERROR
                12501 -> "로그인이 취소되었습니다." // SIGN_IN_CANCELLED
                12500 -> "Google Play 서비스 업데이트가 필요합니다." // SIGN_IN_FAILED
                else -> "구글 로그인 중 오류가 발생했습니다. (코드: ${e.statusCode})"
            }
            
            _uiState.update { LoginUiState.Error(errorMessage) }
        } catch (e: Exception) {
            Log.e(TAG, "로그인 처리 중 예외 발생", e)
            _uiState.update { LoginUiState.Error("로그인 중 오류 발생: ${e.message}") }
        }
    }

    /**
     * ID 토큰으로 백엔드 로그인을 시도합니다.
     */
    private fun googleLoginWithIdToken(idToken: String, account: GoogleSignInAccount) {
        viewModelScope.launch {
            try {
                // ID 토큰으로 백엔드 인증 시도
                val result = authRepository.googleLogin(idToken)
                
                when (result) {
                    is AuthResult.Success -> {
                        // 성공: 사용자 정보 저장
                        val user = result.data
                        userRepository.saveUser(user)
                        Log.i(TAG, "백엔드 인증 성공: ${user.nickname}")
                        _uiState.update { LoginUiState.Success(user) }
                    }
                    is AuthResult.Error -> {
                        // 실패: 오류 메시지 처리
                        Log.w(TAG, "백엔드 인증 실패: ${result.message}")
                        
                        _uiState.update { LoginUiState.Error("백엔드 인증 실패: ${result.message}") }
                        // 백엔드 인증 실패 시 Google 로그아웃
                        googleSignInClient.signOut()
                    }
                    is AuthResult.Loading -> {
                        // 이미 로딩 상태이므로 추가 작업 불필요
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "백엔드 인증 중 예외 발생", e)
                _uiState.update { LoginUiState.Error("백엔드 인증 중 오류 발생: ${e.message}") }
                googleSignInClient.signOut()
            }
        }
    }

    /**
     * 로그아웃 처리
     */
    fun signOut() {
        viewModelScope.launch {
            _uiState.update { LoginUiState.Loading }
            
            try {
                // Google 로그아웃
                googleSignInClient.signOut()
                
                // 백엔드 로그아웃 및 로컬 데이터 삭제
                val result = authRepository.signOut()
                if (result is AuthResult.Success) {
                    _uiState.update { LoginUiState.Idle }
                    Log.i(TAG, "로그아웃 성공")
                } else if (result is AuthResult.Error) {
                    _uiState.update { LoginUiState.Error("로그아웃 중 오류 발생: ${result.message}") }
                }
            } catch (e: Exception) {
                _uiState.update { LoginUiState.Error("로그아웃 중 오류 발생: ${e.message}") }
                Log.e(TAG, "로그아웃 중 예외 발생", e)
            }
        }
    }

    /**
     * UI 상태를 Idle로 초기화하는 함수
     */
    fun resetStateToIdle() {
         _uiState.update { LoginUiState.Idle }
    }
} 