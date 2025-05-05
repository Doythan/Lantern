// src/main/java/com/ssafy/lantern/MyApp.kt
package com.ssafy.lantern

import android.app.Application
import androidx.room.Room
import com.ssafy.lantern.data.database.AppDatabase
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyApp : Application() {
    val db: AppDatabase by lazy {
        Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "lantern.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        // db 초기화는 by lazy 블록에서 이루어집니다.
    }
}
