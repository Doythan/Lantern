package com.ssafy.lantern.data.database

import androidx.room.Database
import com.ssafy.lantern.data.model.CallList
import com.ssafy.lantern.data.model.ChatRoom
import com.ssafy.lantern.data.model.Follow
import com.ssafy.lantern.data.model.Messages
import com.ssafy.lantern.data.model.User
import com.ssafy.lantern.data.repository.CallListDao
import com.ssafy.lantern.data.repository.ChatRoomDao
import com.ssafy.lantern.data.repository.FollowDao
import com.ssafy.lantern.data.repository.MessagesDao
import com.ssafy.lantern.data.repository.UserDao

@Database(
    entities = [
        User::class,          // user 테이블
        CallList::class,      // call_list 테이블
        Messages::class,        // message 테이블
        Follow::class,
        ChatRoom::class
    ],
    version = 1,              // 새 엔티티 추가 → 버전 +1
    exportSchema = false
)
abstract class AppDatabase {
    abstract fun userDao(): UserDao
    abstract fun callListDao(): CallListDao
    abstract fun chatRoomDao(): ChatRoomDao
    abstract fun followDao(): FollowDao
    abstract fun messagesDao(): MessagesDao
}