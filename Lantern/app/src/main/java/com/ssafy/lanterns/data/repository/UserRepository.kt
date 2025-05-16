package com.ssafy.lanterns.data.repository

import com.ssafy.lanterns.data.model.ChatRoom
import com.ssafy.lanterns.data.model.User

interface UserRepository {
    suspend fun saveUser(user: User)
    suspend fun getUser(): User?
    suspend fun clearUser()
    suspend fun getCurrentUser(): User?
    suspend fun updateUser(user: User)
    suspend fun updateNickname(userId: Long, nickname: String)
    suspend fun getUserById(userId: Long): User?
    suspend fun updateProfileImageNumber(userId: Long, profileImageNumber: Int)
    suspend fun clearAllLocalData()
    suspend fun saveDisplayMode(isDarkMode: Boolean)
    suspend fun getDisplayMode(): Boolean
} 