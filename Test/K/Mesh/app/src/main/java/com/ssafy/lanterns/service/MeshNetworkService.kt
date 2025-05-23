package com.ssafy.lanterns.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ssafy.lanterns.ui.view.main.MainActivity
import com.ssafy.lanterns.R
import com.ssafy.lanterns.data.repository.MessageRepository
import com.ssafy.lanterns.data.source.ble.mesh.ChatMessage
import com.ssafy.lanterns.data.source.ble.mesh.MeshChatManager
import com.ssafy.lanterns.data.source.ble.mesh.MessageType
import com.ssafy.lanterns.data.source.ble.mesh.NearbyNode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * BLE 메쉬 네트워크를 관리하는 Android 서비스
 * 백그라운드에서 메쉬 네트워크를 유지하고 메시지 전송 및 수신을 처리
 */
@AndroidEntryPoint
class MeshNetworkService : Service() {
    companion object {
        private const val TAG = "MeshNetworkService"
        private const val FOREGROUND_SERVICE_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "mesh_network_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "Mesh Network"
        
        // 상태 관련 Extra 키
        const val EXTRA_COMMAND = "command"
        const val EXTRA_NICKNAME = "nickname"
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        
        // 명령어
        const val COMMAND_START = "start"
        const val COMMAND_STOP = "stop"
        const val COMMAND_UPDATE_NICKNAME = "update_nickname"
    }
    
    // 서비스 바인더
    private val binder = MeshNetworkBinder()
    
    // 코루틴 스코프
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    
    // 메쉬 네트워크 활성화 여부
    private val isNetworkActive = AtomicBoolean(false)
    
    // BLE 관련 객체
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    
    // 메쉬 채팅 매니저
    @Inject
    lateinit var meshChatManager: MeshChatManager
    
    // 연결 상태
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    // 사용자 정보
    private var userNickname: String = "익명"
    private var deviceAddress: String = ""
    
    // 주변 노드 목록
    private val _nearbyNodes = MutableStateFlow<List<NearbyNode>>(emptyList())
    val nearbyNodes: StateFlow<List<NearbyNode>> = _nearbyNodes.asStateFlow()
    
    // DI
    @Inject
    lateinit var messageRepository: MessageRepository
    
    // 서비스 생성
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        
        // BLE 관련 객체 초기화
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        
        // 메쉬 채팅 매니저 상태 관찰
        observeMeshChatManager()
    }
    
    /**
     * 메쉬 채팅 매니저 상태 관찰
     */
    private fun observeMeshChatManager() {
        // 네트워크 활성화 상태 관찰
        meshChatManager.isNetworkActive.observeForever { isActive ->
            isNetworkActive.set(isActive)
            
            _connectionState.value = if (isActive) {
                ConnectionState.CONNECTED
            } else {
                ConnectionState.DISCONNECTED
            }
            
            // 알림 업데이트
            updateNotification()
        }
        
        // 주변 노드 목록 관찰
        meshChatManager.nearbyNodes.observeForever { nodes ->
            serviceScope.launch {
                _nearbyNodes.emit(nodes)
            }
        }
    }
    
    // 서비스 시작
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        
        if (intent != null) {
            val command = intent.getStringExtra(EXTRA_COMMAND)
            
            when (command) {
                COMMAND_START -> {
                    val nickname = intent.getStringExtra(EXTRA_NICKNAME) ?: "익명"
                    val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS) ?: bluetoothAdapter.address ?: ""
                    
                    userNickname = nickname
                    deviceAddress = address
                    
                    startMeshNetwork(nickname)
                }
                COMMAND_STOP -> {
                    stopMeshNetwork()
                }
                COMMAND_UPDATE_NICKNAME -> {
                    val newNickname = intent.getStringExtra(EXTRA_NICKNAME)
                    if (newNickname != null) {
                        userNickname = newNickname
                        updateNickname(newNickname)
                    }
                }
            }
        }
        
        return START_STICKY
    }
    
    // 서비스 바인딩
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    // 서비스 종료
    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        
        // 메쉬 네트워크 중지
        stopMeshNetwork()
        
        // 코루틴 취소
        serviceScope.cancel()
        
        super.onDestroy()
    }
    
    /**
     * 메쉬 네트워크 시작
     * @param nickname 사용자 닉네임
     */
    private fun startMeshNetwork(nickname: String) {
        if (isNetworkActive.get()) {
            Log.d(TAG, "Mesh network already active")
            return
        }
        
        Log.d(TAG, "Starting mesh network with nickname: $nickname")
        
        // 포그라운드 서비스 시작
        startForeground()
        
        // 닉네임 설정
        meshChatManager.setDeviceNickname(nickname)
        
        // 메쉬 네트워크 시작
        try {
            meshChatManager.startNetwork()
            _connectionState.value = ConnectionState.CONNECTING
        } catch (e: Exception) {
            Log.e(TAG, "Error starting mesh network", e)
            _connectionState.value = ConnectionState.ERROR
            stopForeground(true)
        }
    }
    
    /**
     * 메쉬 네트워크 중지
     */
    private fun stopMeshNetwork() {
        if (!isNetworkActive.get()) {
            Log.d(TAG, "Mesh network already inactive")
            return
        }
        
        Log.d(TAG, "Stopping mesh network")
        
        try {
            // 메쉬 네트워크 중지
            meshChatManager.stopNetwork()
            _connectionState.value = ConnectionState.DISCONNECTING
            
            // 포그라운드 서비스 중지
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping mesh network", e)
            _connectionState.value = ConnectionState.ERROR
        }
    }
    
    /**
     * 닉네임 업데이트
     * @param nickname 새 닉네임
     */
    private fun updateNickname(nickname: String) {
        if (!isNetworkActive.get()) {
            Log.d(TAG, "Cannot update nickname: network inactive")
            return
        }
        
        Log.d(TAG, "Updating nickname to: $nickname")
        
        try {
            meshChatManager.setDeviceNickname(nickname)
            updateNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating nickname", e)
        }
    }
    
    /**
     * 그룹 메시지 전송
     * @param content 메시지 내용
     * @return 전송 성공 여부
     */
    fun sendGroupMessage(content: String): Boolean {
        if (!isNetworkActive.get()) {
            Log.e(TAG, "Cannot send message: network inactive")
            return false
        }
        
        Log.d(TAG, "Sending group message: $content")
        
        return try {
            meshChatManager.sendGroupMessage(content)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending group message", e)
            false
        }
    }
    
    /**
     * 1:1 메시지 전송
     * @param recipient 수신자 주소
     * @param content 메시지 내용
     * @return 전송 성공 여부
     */
    fun sendDirectMessage(recipient: Short, content: String): Boolean {
        if (!isNetworkActive.get()) {
            Log.e(TAG, "Cannot send message: network inactive")
            return false
        }
        
        Log.d(TAG, "Sending direct message to $recipient: $content")
        
        return try {
            meshChatManager.sendDirectMessage(recipient, content)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending direct message", e)
            false
        }
    }
    
    /**
     * 포그라운드 서비스 시작
     */
    private fun startForeground() {
        // 알림 채널 생성
        createNotificationChannel()
        
        // 포그라운드 서비스 시작
        val notification = createNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_SERVICE_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(FOREGROUND_SERVICE_ID, notification)
        }
    }
    
    /**
     * 알림 채널 생성
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "BLE 메쉬 네트워크 서비스 알림"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 알림 생성
     * @return 알림 객체
     */
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val state = when (_connectionState.value) {
            ConnectionState.CONNECTED -> "연결됨"
            ConnectionState.CONNECTING -> "연결 중..."
            ConnectionState.DISCONNECTED -> "연결 해제됨"
            ConnectionState.DISCONNECTING -> "연결 종료 중..."
            ConnectionState.ERROR -> "오류 발생"
        }
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("BLE 메쉬 네트워크")
            .setContentText("상태: $state, 닉네임: $userNickname")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    /**
     * 알림 업데이트
     */
    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(FOREGROUND_SERVICE_ID, createNotification())
    }
    
    /**
     * 서비스 바인더 클래스
     */
    inner class MeshNetworkBinder : Binder() {
        fun getService(): MeshNetworkService = this@MeshNetworkService
    }
}

/**
 * 연결 상태 열거형
 */
enum class ConnectionState {
    DISCONNECTED,    // 연결 해제됨
    CONNECTING,      // 연결 중
    CONNECTED,       // 연결됨
    DISCONNECTING,   // 연결 종료 중
    ERROR            // 오류
} 