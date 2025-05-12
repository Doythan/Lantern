package com.ssafy.lanterns.data.repository

import com.ssafy.lanterns.data.model.ChatRoom
import com.ssafy.lanterns.data.model.Messages
import com.ssafy.lanterns.data.model.User
import java.time.LocalDateTime
import javax.inject.Inject

// UserDao를 주입받아 UserRepository 구현
class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val chatRoomDao: ChatRoomDao,
    private val messagesDao: MessagesDao
) : UserRepository {
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

    // 테스트용 사용자 생성 또는 가져오기
    override suspend fun ensureTestUser(): User {
        // 기존 사용자가 있으면 반환
        val existingUser = getUser()
        if (existingUser != null) {
            return existingUser
        }

        // 없으면 테스트 사용자 생성
        val testUser = User(
            userId = 1L,
            nickname = "테스트 사용자",
            deviceId = "test_device_001"
        )
        saveUser(testUser)
        
        // 테스트 상대방들도 생성
        val partner1 = User(
            userId = 2L,
            nickname = "김철수",
            deviceId = "test_device_002"
        )
        userDao.insertUser(partner1)
        
        val partner2 = User(
            userId = 3L,
            nickname = "이영희",
            deviceId = "test_device_003"
        )
        userDao.insertUser(partner2)
        
        val partner3 = User(
            userId = 4L,
            nickname = "박지성",
            deviceId = "test_device_004"
        )
        userDao.insertUser(partner3)
        
        // 테스트 채팅방 생성
        createTestChatRooms()
        
        return testUser
    }
    
    // 테스트 채팅방 생성 함수
    override suspend fun createTestChatRooms(): List<ChatRoom> {
        val currentTime = LocalDateTime.now()
        val chatRooms = mutableListOf<ChatRoom>()
        
        // 첫 번째 채팅방 (김철수)
        val chatRoom1 = ChatRoom(
            chatRoomId = 1L,
            updatedAt = currentTime.minusDays(1),
            participantId = 2L // 김철수
        )
        chatRoomDao.InsertChatRoom(chatRoom1)
        chatRooms.add(chatRoom1)
        
        // 채팅방 1 메시지 추가
        val messages1 = listOf(
            Messages(
                messageId = 1L,
                userId = 1L, // 테스트 사용자
                chatRoomId = 1L,
                text = "안녕하세요 김철수님",
                date = currentTime.minusDays(1).minusHours(2)
            ),
            Messages(
                messageId = 2L,
                userId = 2L, // 김철수
                chatRoomId = 1L,
                text = "안녕하세요! 반갑습니다.",
                date = currentTime.minusDays(1).minusHours(1)
            ),
            Messages(
                messageId = 3L,
                userId = 1L, // 테스트 사용자
                chatRoomId = 1L,
                text = "오늘 날씨가 좋네요",
                date = currentTime.minusDays(1)
            )
        )
        
        messages1.forEach { messagesDao.insertMessage(it) }
        
        // 두 번째 채팅방 (이영희)
        val chatRoom2 = ChatRoom(
            chatRoomId = 2L,
            updatedAt = currentTime.minusHours(12),
            participantId = 3L // 이영희
        )
        chatRoomDao.InsertChatRoom(chatRoom2)
        chatRooms.add(chatRoom2)
        
        // 채팅방 2 메시지 추가
        val messages2 = listOf(
            Messages(
                messageId = 4L,
                userId = 3L, // 이영희
                chatRoomId = 2L,
                text = "프로젝트 진행상황 어떤가요?",
                date = currentTime.minusHours(12).minusMinutes(30)
            ),
            Messages(
                messageId = 5L,
                userId = 1L, // 테스트 사용자
                chatRoomId = 2L,
                text = "70% 정도 완료되었습니다.",
                date = currentTime.minusHours(12).minusMinutes(25)
            ),
            Messages(
                messageId = 6L,
                userId = 3L, // 이영희
                chatRoomId = 2L,
                text = "좋네요! 내일 회의 때 발표 준비해주세요.",
                date = currentTime.minusHours(12)
            )
        )
        
        messages2.forEach { messagesDao.insertMessage(it) }
        
        // 세 번째 채팅방 (박지성)
        val chatRoom3 = ChatRoom(
            chatRoomId = 3L,
            updatedAt = currentTime.minusMinutes(30),
            participantId = 4L // 박지성
        )
        chatRoomDao.InsertChatRoom(chatRoom3)
        chatRooms.add(chatRoom3)
        
        // 채팅방 3 메시지 추가
        val messages3 = listOf(
            Messages(
                messageId = 7L,
                userId = 1L, // 테스트 사용자
                chatRoomId = 3L,
                text = "식사 하셨나요?",
                date = currentTime.minusMinutes(45)
            ),
            Messages(
                messageId = 8L,
                userId = 4L, // 박지성
                chatRoomId = 3L,
                text = "네, 방금 먹었습니다.",
                date = currentTime.minusMinutes(42)
            ),
            Messages(
                messageId = 9L,
                userId = 1L, // 테스트 사용자
                chatRoomId = 3L,
                text = "오늘 미팅 몇시에 하기로 했죠?",
                date = currentTime.minusMinutes(38)
            ),
            Messages(
                messageId = 10L,
                userId = 4L, // 박지성
                chatRoomId = 3L,
                text = "3시에 하기로 했어요. 통화로 할까요?",
                date = currentTime.minusMinutes(30)
            )
        )
        
        messages3.forEach { messagesDao.insertMessage(it) }
        
        // 채팅방 시간 업데이트
        chatRoomDao.updateChatRoomUpdateAt(1L)
        chatRoomDao.updateChatRoomUpdateAt(2L)
        chatRoomDao.updateChatRoomUpdateAt(3L)
        
        return chatRooms
    }
} 