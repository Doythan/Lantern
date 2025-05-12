package com.ssafy.lanterns.data.repository

import android.util.Log
import com.ssafy.lanterns.data.model.GoogleAuthRequest
import com.ssafy.lanterns.data.model.User
import com.ssafy.lanterns.data.source.remote.AuthService
import com.ssafy.lanterns.data.source.token.TokenManager
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuthRepositoryGoogleImpl"

@Singleton
class AuthRepositoryGoogleImpl @Inject constructor(
    private val authService: AuthService,
    private val tokenManager: TokenManager,
    private val userRepository: UserRepository
) : AuthRepository {

    override suspend fun googleLogin(idToken: String): AuthResult<User> {
        return try {
            val googleAuthRequest = GoogleAuthRequest(idToken = idToken)
            val response = authService.googleLogin(googleAuthRequest)
            
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                
                // JWT 토큰 및 사용자 정보 저장
                tokenManager.saveAccessToken(authResponse.jwt)
                tokenManager.saveUserInfo(
                    userId = authResponse.userId,
                    email = authResponse.email,
                    nickname = authResponse.nickname
                )
                
                // User 객체 생성 및 반환
                val user = User(
                    userId = authResponse.userId,
                    nickname = authResponse.nickname,
                    deviceId = "" // 구글 로그인에서는 deviceId가 필요 없음, 빈 문자열로 설정
                )
                
                AuthResult.Success(user)
            } else {
                val errorMessage = response.errorBody()?.string() ?: "구글 로그인 실패"
                AuthResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "구글 로그인 중 오류 발생: ${e.message}", e)
            
            // 네트워크 오류 등의 경우 테스트 사용자로 자동 로그인 시도
            return loginWithTestUser()
        }
    }

    /**
     * 테스트 사용자로 로그인합니다. 네트워크 오류 등 서버 연결이 불가능한 경우 사용됩니다.
     */
    private suspend fun loginWithTestUser(): AuthResult<User> {
        return try {
            Log.i(TAG, "테스트 사용자로 오프라인 로그인 시도")
            
            // 테스트 사용자 생성 또는 가져오기
            val testUser = userRepository.ensureTestUser()
            
            // 테스트 사용자 정보 저장
            tokenManager.saveAccessToken("test_token_${testUser.userId}")
            tokenManager.saveUserInfo(
                userId = testUser.userId,
                email = "test@example.com",
                nickname = testUser.nickname
            )
            
            Log.i(TAG, "테스트 사용자 로그인 성공: ${testUser.nickname}")
            AuthResult.Success(testUser)
        } catch (e: Exception) {
            Log.e(TAG, "테스트 사용자 로그인 실패", e)
            AuthResult.Error("오프라인 모드 로그인 실패: ${e.message}")
        }
    }

    override suspend fun logout(): AuthResult<Unit> {
        return try {
            tokenManager.clearTokenAndUserInfo()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "로그아웃 중 오류가 발생했습니다")
        }
    }
    
    // 기존 호환성을 위해 logout을 호출하는 signOut 메서드
    override suspend fun signOut(): AuthResult<Unit> {
        return logout()
    }

    override suspend fun isLoggedIn(): Boolean {
        return tokenManager.isLoggedIn()
    }

    override suspend fun getCurrentUserId(): Long? {
        return tokenManager.getUserId()
    }

    override suspend fun getCurrentUserEmail(): String? {
        return tokenManager.getEmail()
    }

    override suspend fun getCurrentUserNickname(): String? {
        return tokenManager.getNickname()
    }
} 