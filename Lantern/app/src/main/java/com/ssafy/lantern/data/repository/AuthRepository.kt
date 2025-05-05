package com.ssafy.lantern.data.repository

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.ssafy.lantern.R
import com.ssafy.lantern.data.model.User
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

interface AuthRepository {
    // 백엔드에 ID 토큰을 보내 검증하고 사용자 정보를 받아오는 함수
    suspend fun verifyGoogleTokenAndLogin(idToken: String): Result<User>

    // 로그아웃 함수 시그니처 추가
    suspend fun signOut(): Result<Unit>
}

// Hilt Singleton으로 지정
@Singleton
class AuthRepositoryImpl @Inject constructor(
    // Application Context 주입
    @ApplicationContext private val context: Context,
    // UserRepository 주입 (DataModule 등에서 제공되어야 함)
    private val userRepository: UserRepository
) : AuthRepository {

    // GoogleSignInClient 인스턴스 (지연 초기화)
    private val googleSignInClient: GoogleSignInClient by lazy {
        // strings.xml에서 Web Client ID 가져오기
        val serverClientId = context.getString(R.string.your_web_client_id)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(serverClientId)
            .requestEmail() // MyPage 등에서 이메일 필요 시 유지
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    override suspend fun verifyGoogleTokenAndLogin(idToken: String): Result<User> {
        return try {
            // --- 실제 구현 시 이 부분을 수정 --- 
            // 1. Retrofit 등을 사용하여 백엔드 API 호출
            // 예: val response = apiService.verifyGoogleToken(mapOf("idToken" to idToken))
            // 2. 응답 성공/실패 처리
            // if (response.isSuccessful && response.body() != null) { ... } else { ... }

            // --- 가상 로직 시작 ---
            println("백엔드로 전송할 ID Token (앞 20자): ${idToken.substring(0, 20)}...")
            delay(1500) // 네트워크 딜레이 흉내

            // 가상의 성공 응답 생성 (실제 백엔드 응답 구조에 맞게 User 모델 생성)
            val simulatedUserId = 12345L
            val simulatedNickname = "Google로그인유저"
            // deviceId는 백엔드에서 오지 않는다고 가정 -> 임시값 또는 빈 값 사용
            val simulatedUser = User(
                userId = simulatedUserId,
                nickname = simulatedNickname,
                deviceId = "temp_device_id_google_auth_repo"
            )
            println("백엔드 통신 성공 (가상): User ID=${simulatedUserId}, Nickname=${simulatedNickname}")
            Result.success(simulatedUser)
            // --- 가상 로직 끝 ---

            // --- 가상 실패 처리 예시 ---
            // Result.failure(Exception("백엔드 인증 실패 (가상)"))
            // --- ---
        } catch (e: Exception) {
            // 네트워크 오류 또는 기타 예외 처리
            println("백엔드 통신 중 오류 발생 (가상): ${e.message}")
            Result.failure(e)
        }
    }

    // signOut 함수 구현
    override suspend fun signOut(): Result<Unit> {
        return try {
            googleSignInClient.signOut().await() // Google 로그아웃
            // FIXME: UserRepository에 clearUser() 함수 구현 필요
            // userRepository.clearUser() // 로컬 DB 사용자 정보 삭제
            println("AuthRepo: Google 로그아웃 및 로컬 데이터 삭제 (가상) 성공")
            Result.success(Unit)
        } catch (e: Exception) {
            println("AuthRepo: 로그아웃 중 오류 발생: ${e.message}")
            Result.failure(e)
        }
    }
} 