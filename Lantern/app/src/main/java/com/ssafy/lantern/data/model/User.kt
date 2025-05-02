package com.ssafy.lantern.data.model

import android.provider.ContactsContract
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user")
data class User(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: Int,

    val nickname: String,

    val email: String,

    val password: String,

    @ColumnInfo(name = "device_id")
    val deviceId: String
)
