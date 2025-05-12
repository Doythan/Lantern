package com.ssafy.lanterns.data.repository

import com.ssafy.lanterns.data.model.ChatRoom
import com.ssafy.lanterns.data.model.User

interface UserRepository {
    suspend fun saveUser(user: User)
    suspend fun getUser(): User?
    suspend fun clearUser()
    suspend fun getCurrentUser(): User?
    suspend fun updateUser(user: User)
    suspend fun ensureTestUser(): User
    
    // 테스트 채팅방 생성 함수 추가
    suspend fun createTestChatRooms(): List<ChatRoom>
} 