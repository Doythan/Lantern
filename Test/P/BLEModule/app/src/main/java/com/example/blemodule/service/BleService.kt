package com.example.blemodule.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.blemodule.R // R 클래스 임포트
import com.example.blemodule.data.repository.BleRepository
import com.example.blemodule.data.state.AdvertisingState
import com.example.blemodule.ui.view.main.MainActivity // 알림 클릭 시 열릴 Activity
import com.example.blemodule.util.BluetoothUtils
import com.example.blemodule.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * BLE 관련 작업을 백그라운드에서 처리하는 Foreground Service 입니다.
 * 스캔, 광고, GATT 서버 운영 등을 담당하며, BleRepository 를 관리합니다.
 * LifecycleService 를 상속받아 LifecycleOwner 로서 lifecycleScope 를 사용할 수 있습니다.
 */
class BleService : LifecycleService() {

    private val TAG = "BleService"
    // 서비스 내부에서 사용할 CoroutineScope 정의 (앱 생명주기와 별개)
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // BleRepository 인스턴스 (늦은 초기화)
    // Hilt 미사용으로 인해 직접 생성
    private lateinit var repository: BleRepository

    // Binder 객체 (Activity 와 Service 간 통신용)
    private val binder = LocalBinder()

    // 서비스 실행 상태 플래그
    private var isServiceRunning = false
    private var currentDeviceId: String? = null

    /** Activity 에서 ‘돌고 있니?’ 확인용 */
    fun isRunning(): Boolean = isServiceRunning

    /** bind-only 상태에서 Activity 가 강제로 BLE 작업을 켜고
     *  Foreground 로 승격시키고 싶을 때 호출  */
    fun startBleOperationsFromClient() {
        if (!isServiceRunning) {
            repository.setServiceActive(true)
            startBleOperations()           // 광고·스캔·Gatt 서버
            startForegroundWithNotification()
            isServiceRunning = true
        }
    }

    /**
     * Service 와 Activity 간의 통신을 위한 Binder 클래스 정의
     */
    inner class LocalBinder : Binder() {
        // Service 인스턴스 반환 (Activity 에서 Service 함수 직접 호출 가능)
        fun getService(): BleService = this@BleService
        // Repository 인스턴스 제공 (Activity/ViewModel 에서 직접 접근) - 권장되지는 않음
        // fun getRepository(): BleRepository = repository
    }

    // --- LifecycleService 생명주기 콜백 ---

    override fun onCreate() {
        super.onCreate()
        log("Service onCreate()")
        // Repository 초기화 (Context 와 Service Scope 전달)
        repository = BleRepository(applicationContext, serviceScope)
        // Repository 의 로그 메시지를 서비스 로그로 출력 (선택적)
        repository.logMessages
            .onEach { log(it) } // Repository 로그를 Service 로그에도 남김
            .launchIn(lifecycleScope) // Service 의 LifecycleScope 사용
    }

    /**
     * startService() 또는 startForegroundService() 호출 시 실행됩니다.
     * 서비스 시작 명령을 처리합니다.
     * @param intent 시작 시 전달된 Intent (명령 포함 가능)
     * @param flags 추가 플래그
     * @param startId 서비스 시작 요청 고유 ID
     * @return 서비스 종료 시 재시작 정책 (START_STICKY: 비정상 종료 시 시스템이 재시작)
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId) // 반드시 호출해야 함
        log("Service onStartCommand()")

        // Intent 에서 데이터 추출 (예: 기기 ID)
        val deviceId = intent?.getStringExtra(EXTRA_DEVICE_ID)

        // 서비스 시작 로직 (이미 실행 중이면 중복 실행 방지)
        if (!isServiceRunning && !deviceId.isNullOrBlank()) {
            currentDeviceId = deviceId
            repository.setMyDeviceId(deviceId)
            repository.setServiceActive(true)    // ① 먼저 활성화
            startBleOperations()                 // ② 이제 모든 매니저가 정상적으로 시작됨
            isServiceRunning = true
            // Foreground Service 시작
            startForegroundWithNotification()
        } else if (intent?.action == ACTION_STOP_SERVICE) {
            // 서비스 중지 액션 처리
            log("서비스 중지 액션 수신")
            stopBleService()
        } else {
            log("서비스가 이미 실행 중이거나 유효한 Device ID가 없습니다.")
        }

        // START_STICKY: 서비스가 비정상 종료될 경우 시스템이 서비스를 다시 시작하지만,
        // Intent 는 null 로 전달됩니다. 서비스 상태 복구 로직이 필요할 수 있습니다.
        return START_STICKY
    }

    /**
     * bindService() 호출 시 실행됩니다.
     * Activity 와의 바인딩을 설정합니다.
     * @param intent 바인딩 요청 Intent
     * @return Binder 객체 (Activity 에서 Service 접근 인터페이스)
     */
    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent) // 중요: LifecycleService 는 super.onBind() 호출 필요
        log("Service onBind()")
        return binder
    }

    /**
     * 모든 바인딩이 해제될 때 호출됩니다.
     * @param intent 바인딩 해제 Intent
     * @return true: 다음에 bindService() 호출 시 onRebind() 호출, false: onBind() 호출
     */
    override fun onUnbind(intent: Intent?): Boolean {
        log("Service onUnbind()")
        // 필요시 바인딩 해제 관련 로직 추가
        return super.onUnbind(intent)
    }

    /**
     * 서비스가 소멸될 때 호출됩니다.
     * 자원 정리 작업을 수행합니다.
     */
    override fun onDestroy() {
        log("Service onDestroy()")
        repository.cleanup() // Repository 자원 정리
        serviceScope.cancel() // 내부 CoroutineScope 취소
        isServiceRunning = false
        super.onDestroy()
    }

    // --- 서비스 제어 함수 (Binder 를 통해 Activity 에서 호출 가능) ---

    /** BLE 관련 작업(스캔, 광고, 서버)을 시작합니다. */
    private fun startBleOperations() {
        if (!BluetoothUtils.isBluetoothEnabled(this)) {
            log("블루투스가 비활성화되어 BLE 작업을 시작할 수 없습니다.")
            // TODO: 사용자에게 블루투스 활성화 요청 알림 또는 UI 피드백 필요
            stopBleService() // 시작 불가 시 서비스 종료
            return
        }
        log("BLE 작업 시작: 스캔, 광고, GATT 서버")
        // 1) 서버 시작
        repository.startGattServer()

        // 2) 광고 시작
        repository.startAdvertising()

        repository.startScanning()

    }

    /** BLE 관련 작업을 중지합니다. */
    private fun stopBleOperations() {
        log("BLE 작업 중지: 스캔, 광고, GATT 서버")
        repository.stopScanning()
        repository.stopAdvertising()
        repository.stopGattServer()
        repository.disconnectAll() // 모든 연결 해제
    }

    /** 서비스 전체를 중지합니다. */
    fun stopBleService() {
        log("BLE 서비스 중지 요청")
        stopBleOperations()
        repository.setServiceActive(false) // Repository 에 서비스 비활성 알림
        isServiceRunning = false
        currentDeviceId = null
        // Foreground Service 중지 (알림 제거)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE) // 알림 제거하며 중지
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true) // 알림 제거하며 중지 (구 버전)
        }
        // 서비스 자체 종료
        stopSelf()
    }

    /** Repository 인스턴스를 외부(Binder)에 제공합니다. */
    fun getRepositoryInstance(): BleRepository {
        // BleRepository 가 초기화되지 않은 경우 예외 발생 방지
        if (!::repository.isInitialized) {
            // 서비스가 완전히 생성되기 전에 접근 시도 시 예외 발생 가능
            // 이 경우 기본 객체를 반환하거나 예외를 던지는 대신 null을 반환하는 것이 안전할 수 있음
            // 또는, Activity/ViewModel 에서 접근 시점에 초기화 여부를 확인하도록 설계 변경
            Log.e(TAG, "Repository 가 아직 초기화되지 않았습니다!")
            // 임시 방편으로 여기서 초기화 시도 (권장하지 않음)
            repository = BleRepository(applicationContext, serviceScope)
            // throw IllegalStateException("Repository is not initialized yet.")
        }
        return repository
    }

    // --- Foreground Service 관련 ---

    /** Foreground Service 를 시작하고 알림을 표시합니다. */
    private fun startForegroundWithNotification() {
        // 알림 채널 생성 (Android 8.0 이상 필수)
        createNotificationChannel()

        // 알림 클릭 시 MainActivity 를 열도록 PendingIntent 설정
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        // 알림 빌드
        val notification: Notification = NotificationCompat.Builder(this, Constants.SERVICE_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.ble_service_notification_title)) // 알림 제목 (strings.xml)
            .setContentText(getString(R.string.ble_service_notification_text)) // 알림 내용 (strings.xml)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 작은 아이콘 (drawable) - 실제 아이콘으로 교체 필요
            .setContentIntent(pendingIntent) // 클릭 시 실행될 Intent
            .setOngoing(true) // 사용자가 스와이프로 제거할 수 없도록 설정
            // .setTicker("BLE 서비스 시작됨") // 상태바에 잠시 표시되는 텍스트 (오래된 방식)
            // .addAction(...) // 알림에 액션 버튼 추가 가능 (예: 서비스 중지 버튼)
            .build()

        // startForeground 호출
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 이상: foregroundServiceType 명시 필요 (Manifest 에도 선언)
            try {
                startForeground(Constants.SERVICE_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                log("Foreground 서비스 시작 (API 29+)")
            } catch (e: Exception) {
                // Android 14 (API 34) 부터 FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE 사용 시
                // Manifest 에 FOREGROUND_SERVICE_CONNECTED_DEVICE 권한 필요
                Log.e(TAG, "startForeground 실패 (API 29+): ${e.message}")
                // 대체 방안 또는 사용자 알림 필요
                // 예: 일반 startForeground 사용 시도 (권한 없으면 실패 가능성)
                try { startForeground(Constants.SERVICE_NOTIFICATION_ID, notification) } catch (e2: Exception) { Log.e(TAG, "대체 startForeground 실패: ${e2.message}") }
            }
        } else {
            // Android 9 이하
            startForeground(Constants.SERVICE_NOTIFICATION_ID, notification)
            log("Foreground 서비스 시작 (API 28-)")
        }
    }

    /** 알림 채널을 생성합니다. (Android 8.0 Oreo 이상 필수) */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                Constants.SERVICE_NOTIFICATION_CHANNEL_ID,
                getString(R.string.service_notification_channel_name), // 채널 이름 (strings.xml)
                NotificationManager.IMPORTANCE_LOW // 알림 중요도 (LOW: 소리 없음, 상태바 아이콘만 표시)
            )
            serviceChannel.description = getString(R.string.service_notification_channel_description) // 채널 설명 (strings.xml)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
            log("알림 채널 생성됨.")
        }
    }

    /** 로그 메시지 출력 */
    private fun log(message: String) {
        Log.d(TAG, message)
    }

    // --- Companion Object (상수 및 Service 시작/중지 Intent 생성 함수) ---
    companion object {
        private const val EXTRA_DEVICE_ID = "com.example.blemodule.service.extra.DEVICE_ID"
        private const val ACTION_STOP_SERVICE = "com.example.blemodule.service.action.STOP_SERVICE"

        /**
         * BleService 를 시작하는 Intent 를 생성합니다.
         * @param context 컨텍스트
         * @param deviceId 이 기기의 고유 ID
         * @return Service 시작용 Intent
         */
        fun newStartIntent(context: Context, deviceId: String): Intent {
            return Intent(context, BleService::class.java).apply {
                putExtra(EXTRA_DEVICE_ID, deviceId)
            }
        }

        /**
         * BleService 를 중지하는 Intent 를 생성합니다.
         * @param context 컨텍스트
         * @return Service 중지용 Intent
         */
        fun newStopIntent(context: Context): Intent {
            return Intent(context, BleService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
        }
    }
}