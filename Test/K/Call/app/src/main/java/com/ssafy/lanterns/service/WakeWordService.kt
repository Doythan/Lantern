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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ssafy.lanterns.BuildConfig
import com.ssafy.lanterns.R
import com.ssafy.lanterns.utils.WakeWordUtils
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class WakeWordService : Service() {

    companion object {
        private const val TAG = "WakeWordService"
        const val ACTION_ACTIVATE_AI = "com.ssafy.lanterns.service.ACTION_ACTIVATE_AI"
        const val ACTION_PAUSE_PORCUPINE = "com.ssafy.lanterns.service.ACTION_PAUSE_PORCUPINE"
        const val ACTION_RESUME_PORCUPINE = "com.ssafy.lanterns.service.ACTION_RESUME_PORCUPINE"
    }

    private var porcupineManager: PorcupineManager? = null

    private val ACCESS_KEY = BuildConfig.PV_ACCESS_KEY
    private val KEYWORD_ASSET_FILENAME = BuildConfig.PV_KEYWORD_FILE
    private val MODEL_ASSET_FILENAME = "porcupine_params.pv"

    private val INTERNAL_KEYWORD_FILENAME = "wakeword_keyword.ppn"
    private val INTERNAL_MODEL_FILENAME = "wakeword_model.pv"

    private var isPorcupinePaused = false

    private val wakeWordCallback = PorcupineManagerCallback { keywordIndex ->
        if (isPorcupinePaused) {
            Log.d(TAG, "Porcupine 일시 중지 중, 웨이크워드 감지 무시됨.")
            return@PorcupineManagerCallback
        }
        if (keywordIndex == 0) {
            Log.i(TAG, "★★★ '헤이 랜턴' 웨이크워드 감지됨! (keywordIndex: $keywordIndex) ★★★")
            val intent = Intent(ACTION_ACTIVATE_AI)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            Log.d(TAG, "ACTION_ACTIVATE_AI 로컬 브로드캐스트 전송됨.")
        }
    }

    private fun copyAssetToInternalStorage(context: Context, assetFileName: String, internalFileName: String): String? {
        val assetManager = context.assets
        val outputFile = File(context.filesDir, internalFileName)

        try {
            BufferedInputStream(assetManager.open(assetFileName)).use { inputStream ->
                BufferedOutputStream(FileOutputStream(outputFile)).use { outputStream ->
                    val buffer = ByteArray(1024)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                }
            }
            Log.i(TAG, "성공: '$assetFileName' -> '${outputFile.absolutePath}' 복사 완료")
            return outputFile.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "'$assetFileName' 파일을 '$internalFileName'(으)로 복사 실패", e)
            return null
        }
    }

    override fun onCreate() {
        if (!WakeWordUtils.hasModelFiles(this)) {
            Log.w(TAG, "모델 파일(.pv/.ppn) 미발견 → 서비스 중지")
            stopSelf()
            return
        }

        super.onCreate()
        Log.d(TAG, "onCreate() 호출됨")

        startForegroundServiceNotification()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO 권한 없음. 서비스 중단.")
            stopSelf()
            return
        }

        Log.d(TAG, "RECORD_AUDIO 권한 확인됨.")
        initializePorcupine()
    }

    private fun initializePorcupine() {
        try {
            Log.d(TAG, "모델 파일 복사 시도: $MODEL_ASSET_FILENAME -> $INTERNAL_MODEL_FILENAME")
            val modelPath = copyAssetToInternalStorage(applicationContext, MODEL_ASSET_FILENAME, INTERNAL_MODEL_FILENAME)

            Log.d(TAG, "키워드 파일 복사 시도: $KEYWORD_ASSET_FILENAME -> $INTERNAL_KEYWORD_FILENAME")
            val keywordPath = copyAssetToInternalStorage(applicationContext, KEYWORD_ASSET_FILENAME, INTERNAL_KEYWORD_FILENAME)

            if (modelPath == null || keywordPath == null) {
                Log.e(TAG, "모델 또는 키워드 파일 내부 저장소로 복사 실패. 서비스 중단.")
                stopSelf()
                return
            }

            Log.i(TAG, "내부 저장소 모델 파일 경로: $modelPath")
            Log.i(TAG, "내부 저장소 키워드 파일 경로: $keywordPath")

            porcupineManager?.let {
                try {
                    it.stop()
                    it.delete()
                } catch (e: PorcupineException) {
                    Log.w(TAG, "기존 PorcupineManager 해제 중 오류 발생 (무시): ${e.message}")
                }
                porcupineManager = null
            }

            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(ACCESS_KEY)
                .setModelPath(modelPath)
                .setKeywordPath(keywordPath)
                .setSensitivity(0.7f)
                .build(applicationContext, wakeWordCallback)

            Log.i(TAG, "PorcupineManager 초기화 성공 (내부 저장소 파일 사용).")
        } catch (e: PorcupineException) {
            Log.e(TAG, "PorcupineManager 초기화 중 예외 발생: ${e.message}", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand() 호출됨. Action: ${intent?.action}, Flags: $flags, StartId: $startId")

        when (intent?.action) {
            ACTION_PAUSE_PORCUPINE -> {
                pausePorcupine()
                return START_NOT_STICKY
            }
            ACTION_RESUME_PORCUPINE -> {
                resumePorcupine()
                return START_STICKY
            }
            else -> {
                if (porcupineManager == null &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "onStartCommand: porcupineManager가 null이므로 재초기화 시도.")
                    initializePorcupine()
                }
                startPorcupineListening()
            }
        }
        return START_STICKY
    }

    private fun startPorcupineListening() {
        if (isPorcupinePaused) {
            Log.d(TAG, "Porcupine이 일시 중지 상태이므로 시작하지 않음.")
            return
        }
        if (porcupineManager == null) {
            Log.e(TAG, "startPorcupineListening: PorcupineManager가 null입니다.")
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "권한 있음 → 재초기화 시도")
                initializePorcupine()
                if (porcupineManager == null) {
                    Log.e(TAG, "재초기화 후에도 null입니다.")
                    return
                }
            } else {
                Log.e(TAG, "권한 없음 → 시작 불가")
                return
            }
        }

        try {
            porcupineManager?.start()
            Log.i(TAG, "Porcupine 음성 감지 시작됨.")
        } catch (e: PorcupineException) {
            Log.e(TAG, "Porcupine 시작 중 예외 발생: ${e.message}", e)
        }
    }

    fun pausePorcupine() {
        if (!isPorcupinePaused && porcupineManager != null) {
            try {
                porcupineManager?.stop()
                isPorcupinePaused = true
                Log.i(TAG, "Porcupine 음성 감지 일시 중지됨.")
            } catch (e: PorcupineException) {
                Log.e(TAG, "Porcupine 중지 중 예외 발생: ${e.message}", e)
            }
        } else {
            Log.d(TAG, "이미 중지 상태 or null")
        }
    }

    fun resumePorcupine() {
        if (isPorcupinePaused) {
            isPorcupinePaused = false
            Log.i(TAG, "Porcupine 음성 감지 재개 시도.")
            if (porcupineManager == null) {
                Log.w(TAG, "manager null → 초기화 시도")
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    initializePorcupine()
                } else {
                    Log.e(TAG, "권한 없음 → 초기화 불가")
                    return
                }
            }
            if (porcupineManager != null) {
                startPorcupineListening()
            } else {
                Log.e(TAG, "manager 여전히 null → 시작 불가")
            }
        } else {
            Log.d(TAG, "이미 실행 중")
            if (porcupineManager == null) {
                Log.w(TAG, "manager 없음 → 초기화 시도")
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    initializePorcupine()
                    if (porcupineManager != null) startPorcupineListening()
                } else {
                    Log.e(TAG, "권한 없음 → 초기화 불가")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
        } catch (e: PorcupineException) {
            Log.e(TAG, "리소스 해제 중 예외 발생: ${e.message}", e)
        }
        porcupineManager = null
        Log.d(TAG, "onDestroy() 호출됨. Porcupine 리소스 해제 완료.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundServiceNotification() {
        val CHANNEL_ID = "WakeWordServiceChannel"
        val NOTIFICATION_ID = 1

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "웨이크워드 감지 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "앱이 '헤이 랜턴' 웨이크워드를 감지하고 있습니다."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }

        val notificationIcon = R.drawable.lantern_logo

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("'헤이 랜턴' 웨이크워드 감지 중...")
            .setSmallIcon(notificationIcon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        try {
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "Foreground 서비스 알림 표시됨.")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground 예외 발생: ${e.message}", e)
        }
    }
}
