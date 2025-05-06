package com.ssafy.lantern.ui.screens.login // 패키지 경로는 실제 프로젝트 구조에 맞게 조정하세요

import android.app.Application // Application Context 사용
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
import com.ssafy.lantern.R // R 클래스 임포트
import com.ssafy.lantern.data.model.User // 실제 User 모델 경로로 수정하세요
import com.ssafy.lantern.data.repository.AuthRepository // 생성된 Repository 임포트
import com.ssafy.lantern.data.repository.UserRepository // 생성된 Repository 임포트
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext // ApplicationContext 임포트
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// 로그인 상태를 나타내는 Sealed Interface (변경 없음)
sealed interface LoginUiState {
    object Idle : LoginUiState
    object Loading : LoginUiState
    data class Success(val user: User) : LoginUiState
    data class Error(val message: String) : LoginUiState
}

@HiltViewModel // Hilt 사용 명시
class LoginViewModel @Inject constructor(
    // Application Context 주입 (Activity Context 대신 사용)
    @ApplicationContext private val applicationContext: Context,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // serverClientId를 strings.xml에서 가져오기
    private val serverClientId: String by lazy {
        applicationContext.getString(R.string.your_web_client_id)
    }

    // GoogleSignInClient 인스턴스 (지연 초기화)
    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            // 서버로부터 ID 토큰 요청 (requestIdToken)
            .requestIdToken(serverClientId)
            // 이메일 주소 요청
            .requestEmail()
            // 프로필 정보 요청 추가
            .requestProfile()
            .build()
        
        Log.d("LoginViewModel", "GoogleSignInOptions 생성 - serverClientId: $serverClientId")
        GoogleSignIn.getClient(applicationContext, gso)
    }

    /**
     * Google 로그인 인텐트를 반환합니다.
     */
    fun getSignInIntent(): Intent {
        // 로그인 시도 전에 이전 로그인 상태를 초기화하여 항상 로그인 화면이 표시되도록 함
        googleSignInClient.signOut().addOnCompleteListener {
            Log.d("LoginViewModel", "이전 Google 로그인 세션 로그아웃 완료")
        }
        
        return googleSignInClient.signInIntent
    }

    /**
     * ActivityResult 로부터 받은 로그인 결과를 처리합니다.
     */
    fun handleSignInResult(data: Intent?) {
        _uiState.update { LoginUiState.Loading }
        
        // 데이터가 null인 경우 처리
        if (data == null) {
            Log.e("LoginViewModel", "로그인 결과 Intent가 null입니다")
            _uiState.update { LoginUiState.Error("로그인 처리 중 오류가 발생했습니다.") }
            return
        }
        
        val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
            Log.i("LoginViewModel", "Google Sign In successful for: ${account.email}")
            
            // ID 토큰 가져오기
            val idToken = account.idToken
            if (idToken != null) {
                Log.d("LoginViewModel", "ID 토큰 획득 성공: ${idToken.substring(0, 15)}...")
                // 백엔드 인증 및 Room 저장
                verifyTokenWithBackend(idToken)
            } else {
                Log.e("LoginViewModel", "Google ID Token is null - 권한 설정 또는 프로젝트 설정 문제 가능성")
                _uiState.update { LoginUiState.Error("Google 로그인 정보를 가져오지 못했습니다. (ID 토큰 없음)") }
            }
        } catch (e: ApiException) {
            // 로그인 실패 처리
            Log.e("LoginViewModel", "Google sign in failed with status code: ${e.statusCode}", e)
            
            // 사용자 취소(12501), 네트워크 오류(7), 개발자 오류(10, 16) 등 상태 코드 확인
            val errorMessage = when (e.statusCode) {
                10 -> "앱이 Google에 등록되지 않았거나 설정 오류입니다. SHA-1 키를 확인하세요." // DEVELOPER_ERROR
                16 -> "앱에 대한 적절한 인증 설정이 없습니다. Firebase 콘솔에서 SHA-1 키를 확인하세요." // INTERNAL_ERROR
                7 -> "네트워크 오류가 발생했습니다. 인터넷 연결을 확인하세요." // NETWORK_ERROR
                12501 -> "로그인이 취소되었습니다." // SIGN_IN_CANCELLED
                12500 -> "Google Play 서비스 업데이트가 필요합니다." // SIGN_IN_FAILED
                else -> "Google 로그인 중 오류가 발생했습니다. (코드: ${e.statusCode})"
            }
            
            Log.w("LoginViewModel", "Google 로그인 오류 메시지: $errorMessage")
            _uiState.update { LoginUiState.Error(errorMessage) }
        } catch (e: Exception) {
            Log.e("LoginViewModel", "Error handling sign in result", e)
            _uiState.update { LoginUiState.Error("로그인 처리 중 알 수 없는 오류가 발생했습니다: ${e.message}") }
        }
    }

    private fun verifyTokenWithBackend(idToken: String) {
        viewModelScope.launch {
            Log.d("LoginViewModel", "Verifying ID token with backend...")
            val backendResponse = authRepository.verifyGoogleTokenAndLogin(idToken)

            if (backendResponse.isSuccess) {
                val userInfo = backendResponse.getOrThrow()
                userRepository.saveUser(userInfo)
                Log.i("LoginViewModel", "Backend verification and user save successful for: ${userInfo.nickname}")
                _uiState.update { LoginUiState.Success(userInfo) }
            } else {
                Log.e("LoginViewModel", "Backend token verification failed", backendResponse.exceptionOrNull())
                _uiState.update { LoginUiState.Error("서버 인증에 실패했습니다.") }
                // 백엔드 실패 시 Google 로그아웃 처리 (선택 사항)
                // googleSignInClient.signOut()
            }
        }
    }

    /**
     * 로그아웃 처리 (AuthRepository 사용)
     */
    fun signOut() {
        viewModelScope.launch {
            // 직접 로직 수행 대신 AuthRepository 호출
            val result = authRepository.signOut()
            if (result.isSuccess) {
                _uiState.update { LoginUiState.Idle } // 로그아웃 성공 시 UI 상태 Idle로 변경
            } else {
                _uiState.update { LoginUiState.Error("로그아웃 중 오류 발생: ${result.exceptionOrNull()?.message}") }
            }
        }
    }

    /**
     * UI 상태를 Idle로 초기화하는 함수 (필요 시 사용)
     */
    fun resetStateToIdle() {
         _uiState.update { LoginUiState.Idle }
    }
} 