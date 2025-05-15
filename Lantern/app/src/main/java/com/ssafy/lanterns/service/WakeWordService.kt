package com.ssafy.lanterns.service

import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ssafy.lanterns.R // R 클래스 import (알림 아이콘 등)
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class WakeWordService : Service() {

    private var porcupineManager: PorcupineManager? = null
    // 중요: 여기에 실제 Picovoice Access Key를 입력하세요.
    private val ACCESS_KEY = "yDavJ4DBaKni68lXNor2vRSSwSwPgAjh8wvgMTJ62e7SkLdoOT2eOA==" // 제공된 키 (만료되지 않았는지 확인 필요)

    // assets 폴더 내 원본 파일명
    private val KEYWORD_ASSET_FILENAME = "hey-lantern_en_android_v3_0_0.ppn"
    private val MODEL_ASSET_FILENAME = "porcupine_params.pv"

    // 내부 저장소에 복사될 파일명 (임의로 지정 가능, 충돌 방지를 위해 고유하게)
    private val INTERNAL_KEYWORD_FILENAME = "wakeword_keyword.ppn"
    private val INTERNAL_MODEL_FILENAME = "wakeword_model.pv"

    private val wakeWordCallback = PorcupineManagerCallback { keywordIndex ->
        // Porcupine은 여러 키워드를 동시에 감지할 수 있으며, keywordIndex는 감지된 키워드의 인덱스입니다.
        // 현재는 하나의 키워드만 사용하므로, keywordIndex가 0일 때 "헤이 랜턴"이 감지된 것으로 간주합니다.
        if (keywordIndex == 0) {
            // 웨이크워드 감지 시 로그 출력 (더 눈에 띄도록 수정)
            Log.i("WakeWordService_Detection", "★★★ '헤이 랜턴' 웨이크워드 감지됨! (keywordIndex: $keywordIndex) ★★★")

            // TODO: "헤이 랜턴" 감지 시 실행할 로직 구현
            // 예: 다른 STT 모델 실행, 특정 액티비티 시작, 브로드캐스트 전송 등
            // 이 부분에서 버튼 클릭이나 채팅을 치는 기능을 수행할 다른 STT 모델을 활성화합니다.
            // 예시: Toast.makeText(applicationContext, "'헤이 랜턴' 감지됨!", Toast.LENGTH_SHORT).show()
            // (서비스에서 Toast를 직접 띄우는 것은 UI 스레드에서 해야 하므로 주의)
        }
    }

    // assets에서 내부 저장소로 파일 복사하는 함수
    private fun copyAssetToInternalStorage(context: Context, assetFileName: String, internalFileName: String): String? {
        val assetManager = context.assets
        // getFilesDir()은 앱의 내부 저장소 중 files 디렉토리 경로를 반환합니다.
        // 이 공간은 해당 앱만 접근 가능합니다.
        val outputFile = File(context.filesDir, internalFileName)

        // 최신 파일 유지를 위해 매번 복사 (또는 파일 존재 여부 및 버전 관리 로직 추가 가능)
        // if (outputFile.exists()) {
        //     Log.d("WakeWordService", "$internalFileName already exists in internal storage at ${outputFile.absolutePath}. Skipping copy.")
        //     return outputFile.absolutePath
        // }

        try {
            // assetManager.open()으로 assets 폴더의 파일에 대한 InputStream을 얻습니다.
            BufferedInputStream(assetManager.open(assetFileName)).use { inputStream ->
                // FileOutputStream으로 내부 저장소에 파일을 씁니다.
                BufferedOutputStream(FileOutputStream(outputFile)).use { outputStream ->
                    val buffer = ByteArray(1024) // 1KB 버퍼
                    var read: Int
                    // 파일 끝까지 읽어서 씁니다.
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                }
            }
            Log.i("WakeWordService", "성공: '$assetFileName' -> '${outputFile.absolutePath}' 복사 완료")
            return outputFile.absolutePath // 복사된 파일의 절대 경로 반환
        } catch (e: IOException) {
            Log.e("WakeWordService", "'$assetFileName' 파일을 '$internalFileName'(으)로 복사 실패", e)
            e.printStackTrace() // 에러 스택 트레이스 출력
            return null
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("WakeWordService", "onCreate() 호출됨")

        Log.d("WakeWordService", "Access Key (앞 5자리): ${ACCESS_KEY.take(5)}...") // 전체 키 로깅은 피하는 것이 좋음

        startForegroundServiceNotification()

        // RECORD_AUDIO 권한 확인
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            @Suppress("DEPRECATION")
            stopForeground(true)
            stopSelf()
            return
        }
        Log.d("WakeWordService", "RECORD_AUDIO 권한 확인됨.")

        try {
            // assets 폴더에서 내부 저장소로 필요한 파일들 복사
            Log.d("WakeWordService", "모델 파일 복사 시도: $MODEL_ASSET_FILENAME -> $INTERNAL_MODEL_FILENAME")
            val modelPath = copyAssetToInternalStorage(applicationContext, MODEL_ASSET_FILENAME, INTERNAL_MODEL_FILENAME)

            Log.d("WakeWordService", "키워드 파일 복사 시도: $KEYWORD_ASSET_FILENAME -> $INTERNAL_KEYWORD_FILENAME")
            val keywordPath = copyAssetToInternalStorage(applicationContext, KEYWORD_ASSET_FILENAME, INTERNAL_KEYWORD_FILENAME)

            // 파일 복사 성공 여부 확인
            if (modelPath == null || keywordPath == null) {
                Log.e("WakeWordService", "모델 또는 키워드 파일 내부 저장소로 복사 실패. 서비스 중단.")
                stopSelf()
                return
            }

            Log.i("WakeWordService", "내부 저장소 모델 파일 경로: $modelPath")
            Log.i("WakeWordService", "내부 저장소 키워드 파일 경로: $keywordPath")

            // PorcupineManager 초기화 (내부 저장소의 절대 경로 사용)
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(ACCESS_KEY)
                .setModelPath(modelPath)         // 복사된 모델 파일의 절대 경로
                .setKeywordPath(keywordPath)     // 복사된 키워드 파일의 절대 경로
                .setSensitivity(0.7f)            // 감도 설정 (0.0 ~ 1.0), 값을 조절하며 테스트 필요
                .build(applicationContext, wakeWordCallback)

            Log.i("WakeWordService", "PorcupineManager 초기화 성공 (내부 저장소 파일 사용).")
        } catch (e: PorcupineException) {
            Log.e("WakeWordService", "PorcupineManager 초기화 중 예외 발생: ${e.message}")
            e.printStackTrace()
            stopSelf() // 초기화 실패 시 서비스 중단
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("WakeWordService", "onStartCommand() 호출됨. Flags: $flags, StartId: $startId")

        // Foreground Service 알림 표시 (시스템에 의해 서비스가 쉽게 종료되지 않도록)
        startForegroundServiceNotification()

        // Porcupine 음성 감지 시작
        try {
            if (porcupineManager != null) {
                Log.d("WakeWordService", "porcupineManager.start() 호출 시도")
                porcupineManager?.start() // 마이크 사용 시작
                Log.i("WakeWordService", "Porcupine 음성 감지 시작됨.")
            } else {
                Log.e("WakeWordService", "onStartCommand: PorcupineManager가 null입니다 (초기화 실패 가능성). 서비스 중단 시도.")
                stopSelf() // porcupineManager가 null이면 서비스 의미 없으므로 중단
            }
        } catch (e: PorcupineException) {
            Log.e("WakeWordService", "Porcupine 시작 중 예외 발생: ${e.message}")
            e.printStackTrace()
            // 시작 실패 시 어떻게 처리할지 결정 (예: 재시도 로직, 서비스 중단 등)
        }

        // 서비스가 시스템에 의해 종료될 경우, 시스템이 서비스를 재시작하도록 함
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // Porcupine 리소스 해제
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
        } catch (e: PorcupineException) {
            Log.e("WakeWordService", "Porcupine 리소스 해제 중 예외 발생: ${e.message}")
            e.printStackTrace()
        }
        porcupineManager = null
        Log.d("WakeWordService", "onDestroy() 호출됨. Porcupine 리소스 해제 완료.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Bound Service가 아니므로 null 반환
        return null
    }

    // Foreground Service 알림 설정
    private fun startForegroundServiceNotification() {
        val CHANNEL_ID = "WakeWordServiceChannel"
        val NOTIFICATION_ID = 1 // 서비스마다 고유한 ID 사용

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "웨이크워드 감지 서비스", // 사용자에게 표시될 채널 이름
                NotificationManager.IMPORTANCE_LOW // 알림 중요도 (LOW, DEFAULT, HIGH 등)
            )
            serviceChannel.description = "앱이 '헤이 랜턴' 웨이크워드를 감지하고 있습니다." // 채널 설명
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }

        // 알림 아이콘은 프로젝트의 `res/drawable`에 있는 아이콘으로 변경하세요.
        // 예: R.drawable.ic_mic_white 또는 앱 아이콘
        // TODO: 'R.drawable.lantern_logo'가 실제 존재하는지 확인하고, 없다면 적절한 아이콘으로 교체
        val notificationIcon = R.drawable.lantern_logo // 이 아이콘이 실제 있어야 함

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name)) // 앱 이름 (strings.xml 참조)
            .setContentText("'헤이 랜턴' 웨이크워드 감지 중...")
            .setSmallIcon(notificationIcon)
            .setPriority(NotificationCompat.PRIORITY_LOW) // 중요도 낮은 알림
            .setOngoing(true) // 사용자가 알림을 지울 수 없도록 설정
            .build()

        try {
            startForeground(NOTIFICATION_ID, notification)
            Log.d("WakeWordService", "Foreground 서비스 알림 표시됨.")
        } catch (e: Exception) {
            Log.e("WakeWordService", "startForeground 예외 발생: ${e.message}")
            e.printStackTrace()
            // Android 12 (S, API 31) 이상에서 FOREGROUND_SERVICE_SPECIAL_USE 권한이 필요한 경우 발생 가능성
            // 또는 다른 알림 관련 문제일 수 있음
        }
    }
}