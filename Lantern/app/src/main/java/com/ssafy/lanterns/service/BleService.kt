package com.ssafy.lanterns.service

import android.app.Activity
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.ssafy.lanterns.config.NeighborDiscoveryConstants
import com.ssafy.lanterns.service.ble.advertiser.NeighborAdvertiser
import com.ssafy.lanterns.service.ble.scanner.NeighborScanner

/**
 * 블루투스 저전력(BLE) 스캔과 광고를 관리하는 서비스
 * 앱의 백그라운드 상태에서도 BLE 작업을 계속할 수 있도록 합니다.
 */
class BleService : Service() {
    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())
    private var activity: Activity? = null
    private var serverUserId: Long = -1L
    private var nickname: String = ""
    private var currentDepth: Int = 0
    private var isServiceRunning = false
    
    inner class LocalBinder : Binder() {
        fun getService(): BleService = this@BleService
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    /**
     * 서비스를 초기화하고 시작합니다.
     */
    fun initialize(activity: Activity, serverUserId: Long, nickname: String, initialDepth: Int = 0) {
        this.activity = activity
        this.serverUserId = serverUserId
        this.nickname = nickname
        this.currentDepth = initialDepth
        
        NeighborAdvertiser.init(activity)
        NeighborScanner.init(activity)
        NeighborScanner.setMyNickname(nickname)
        NeighborScanner.setMyServerId(serverUserId)
        
        Log.i("BleService", "서비스 초기화 완료: serverUserId=$serverUserId, nickname='$nickname', initialDepth=$initialDepth")
    }

    /**
     * BLE 스캔 및 광고를 시작합니다.
     */
    fun startBleOperations() {
        if (isServiceRunning) return
        if (serverUserId == -1L) {
            Log.e("BleService", "서버 ID가 설정되지 않아 BLE 작업을 시작할 수 없습니다.")
            return
        }
        
        isServiceRunning = true
        NeighborScanner.startScanning()
        startAdvertising()
        
        // 주기적으로 광고 업데이트
        scheduleNextAdvertising()
        
        Log.i("BleService", "BLE 작업 시작됨 (serverUserId=$serverUserId, depth=$currentDepth)")
    }

    /**
     * BLE 스캔 및 광고를 중지합니다.
     */
    fun stopBleOperations() {
        if (!isServiceRunning) return
        
        handler.removeCallbacksAndMessages(null)
        NeighborScanner.stopScanning()
        NeighborAdvertiser.stopAdvertising()
        isServiceRunning = false
        
        Log.i("BleService", "BLE 작업 중지됨")
    }

    /**
     * 현재 Depth 값을 업데이트합니다.
     */
    fun updateDepth(newDepth: Int) {
        if (currentDepth != newDepth) {
            currentDepth = newDepth
            if (isServiceRunning) {
                startAdvertising() // 즉시 새 Depth로 광고
            }
            Log.i("BleService", "Depth 업데이트됨: $newDepth")
        }
    }

    /**
     * 광고를 시작하고 다음 광고를 예약합니다.
     */
    private fun startAdvertising() {
        if (serverUserId != -1L) {
            NeighborAdvertiser.startAdvertising(serverUserId, nickname, currentDepth)
        }
    }

    /**
     * 주기적인 광고 업데이트를 예약합니다.
     */
    private fun scheduleNextAdvertising() {
        handler.postDelayed({
            if (isServiceRunning) {
                startAdvertising()
                scheduleNextAdvertising()
            }
        }, NeighborDiscoveryConstants.ADVERTISE_INTERVAL_MS)
    }

    /**
     * 특정 화면으로 이동 시 BLE 작업을 일시 중지하고 재개하는 메서드
     * 예: 채팅 화면 진입 시 BLE 일시 중지, 메인 화면 복귀 시 재개
     */
    fun pauseBleForScreen(screenType: String) {
        when (screenType) {
            "CHAT" -> {
                // 채팅 화면에서는 BLE 작업 중지
                stopBleOperations()
                Log.i("BleService", "$screenType 화면에서 BLE 작업 중지")
            }
            "MAIN" -> {
                // 메인 화면으로 돌아오면 BLE 작업 재개
                startBleOperations()
                Log.i("BleService", "$screenType 화면에서 BLE 작업 재개")
            }
        }
    }

    override fun onDestroy() {
        stopBleOperations()
        activity = null
        super.onDestroy()
    }
}