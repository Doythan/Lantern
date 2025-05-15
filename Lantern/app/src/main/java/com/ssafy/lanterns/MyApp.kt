// src/main/java/com/ssafy/lantern/MyApp.kt
package com.ssafy.lanterns

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.room.Room
import com.ssafy.lanterns.data.database.AppDatabase
import com.ssafy.lanterns.service.WakeWordService
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
        Intent(this, WakeWordService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent) // API 26 이상에서는 foreground service로 시작
            } else {
                startService(intent)
            }
        }
    }
}
