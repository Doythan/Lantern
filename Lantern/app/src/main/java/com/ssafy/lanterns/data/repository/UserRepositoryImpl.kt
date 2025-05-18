package com.ssafy.lanterns.data.repository

import android.content.Context
import android.util.Log
import com.ssafy.lanterns.data.model.User
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject

// UserDao를 주입받아 UserRepository 구현
class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val chatRoomDao: ChatRoomDao,
    private val messagesDao: MessagesDao,
    private val followDao: FollowDao,
    private val callListDao: CallListDao,
    @ApplicationContext private val context: Context // Context 주입 추가
) : UserRepository {

    companion object {
        private const val PREFERENCES_NAME = "lantern_preferences"
        private const val KEY_DARK_MODE = "is_dark_mode"
    }

    override suspend fun saveUser(user: User) {
        // 기존 데이터 삭제 후 삽입 (단일 사용자 정보만 저장 가정)
        userDao.deleteAllUsers() // 모든 사용자 삭제 DAO 메소드 추가
        userDao.insertUser(user)
    }

    override suspend fun getUser(): User? {
        // 저장된 사용자 정보가 있다면 반환 (첫번째 항목 가정)
        return userDao.getUserInfo().firstOrNull()
    }

    override suspend fun clearUser() {
        // 모든 사용자 정보 삭제
        userDao.deleteAllUsers()
    }

    override suspend fun getCurrentUser(): User? {
        return getUser()
    }

    override suspend fun updateUser(user: User) {
        saveUser(user)
    }

    override suspend fun updateNickname(userId: Long, nickname: String) {
        userDao.updateNickname(userId, nickname)
    }

    override suspend fun getUserById(userId: Long): User? {
        return userDao.getUserById(userId)
    }

    override suspend fun updateProfileImageNumber(userId: Long, profileImageNumber: Int) {
        userDao.updateProfileImageNumber(userId, profileImageNumber)
    }

    // 모든 로컬 데이터 초기화
    override suspend fun clearAllLocalData() {
        // 트랜잭션으로 모든 데이터 삭제 작업을 묶음
        try {
            // 모든 테이블의 데이터 삭제
            userDao.deleteAllUsers()
            chatRoomDao.deleteAllChatRooms()
            messagesDao.deleteAllMessages()
            followDao.deleteAllFollows()
            callListDao.deleteAllCallLists()
            
            Log.d("UserRepositoryImpl", "모든 로컬 데이터가 성공적으로 초기화되었습니다.")
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "로컬 데이터 초기화 중 오류 발생: ${e.message}")
        }
    }
    
    // 디스플레이 모드(다크/라이트) 저장
    override suspend fun saveDisplayMode(isDarkMode: Boolean) {
        val sharedPrefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putBoolean(KEY_DARK_MODE, isDarkMode)
            apply()
        }
        Log.d("UserRepositoryImpl", "디스플레이 모드 저장: $isDarkMode")
    }
    
    // 디스플레이 모드(다크/라이트) 불러오기
    override suspend fun getDisplayMode(): Boolean {
        val sharedPrefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val isDarkMode = sharedPrefs.getBoolean(KEY_DARK_MODE, true) // 기본값은 다크모드
        Log.d("UserRepositoryImpl", "디스플레이 모드 불러오기: $isDarkMode")
        return isDarkMode
    }
}