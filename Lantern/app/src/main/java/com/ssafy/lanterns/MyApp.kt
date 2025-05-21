package com.ssafy.lanterns

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.room.Room
import com.ssafy.lanterns.data.database.AppDatabase
import com.ssafy.lanterns.data.source.ble.BleManager
import com.ssafy.lanterns.service.WakeWordService
import com.ssafy.lanterns.utils.WakeWordUtils
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MyApp : Application() {
    // BleManager 의존성 주입
    @Inject
    lateinit var bleManager: BleManager

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

        // BLE 서비스 초기화 - 권한 체크는 BleManager 내부에서 수행
        try {
            bleManager.initialize()
            Log.d("MyApp", "BLE 서비스 초기화 완료")
        } catch (e: Exception) {
            Log.e("MyApp", "BLE 서비스 초기화 실패: ${e.message}")
        }

        // WakeWord 모델 파일 존재 여부 확인
        if (WakeWordUtils.hasModelFiles(this)) {
            Intent(this, WakeWordService::class.java).also { intent ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent) // API 26 이상에서는 foreground service로 시작
                } else {
                    startService(intent)
                }
            }
        } else {
            // 모델 파일이 없을 경우 로그를 남기고 서비스 시작 안 함
            Log.w("MyApp", "WakeWord 모델 파일이 없어 WakeWordService를 시작하지 않습니다.")
        }
    }
}