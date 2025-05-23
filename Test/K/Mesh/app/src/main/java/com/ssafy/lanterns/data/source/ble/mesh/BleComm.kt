package com.ssafy.lanterns.data.source.ble.mesh

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ssafy.lanterns.data.source.ble.advertiser.AdvertiserManager
import com.ssafy.lanterns.data.source.ble.scanner.ScannerManager
import java.util.concurrent.TimeUnit

/**
 * BLE 통신 계층 인터페이스
 * 메시 네트워크의 BLE 통신을 담당
 */
interface BleComm {
    /**
     * BLE 광고 시작
     * @param data Network Layer로부터 전달받은 전체 광고될 바이트 배열
     */
    fun startAdvertising(data: ByteArray)
    
    /**
     * BLE 광고 중지
     */
    fun stopAdvertising()
    
    /**
     * BLE 스캔 시작
     * @param callback 스캔 결과를 받아 처리할 함수 (Network Layer에서 Mesh PDU 추출)
     */
    fun startScanning(callback: (ScanResult) -> Unit)
    
    /**
     * BLE 스캔 중지
     */
    fun stopScanning()
}

/**
 * BleComm 인터페이스의 구현체
 * 실제 BLE 광고와 스캔을 관리
 * 10초간 LOW_LATENCY → 1분간 LOW_POWER 주기를 반복하는 스캔 전략 적용
 */
class BleCommImpl(
    private val context: Context,
    private val advertiserManager: AdvertiserManager,
    private val scannerManager: ScannerManager
) : BleComm {
    
    private val TAG = "BleCommImpl"
    
    // 스캔 모드 전환을 위한 Handler
    private val handler = Handler(Looper.getMainLooper())
    
    // 스캔 타이머 관련 상수
    private val HIGH_SCAN_PERIOD = TimeUnit.SECONDS.toMillis(10) // 10초
    private val LOW_SCAN_PERIOD = TimeUnit.MINUTES.toMillis(1) // 1분
    
    // 현재 스캔 중인지 여부
    private var isScanning = false
    
    // 콜백 참조
    private var scanCallback: ScanCallback? = null
    
    // 현재 광고 중인 데이터
    private var currentAdvertisingData: ByteArray? = null
    
    /**
     * 스캔 콜백
     * 콜백을 통해 스캔 결과를 Network Layer로 전달
     */
    private inner class MeshScanCallback(private val callback: (ScanResult) -> Unit) : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            callback(result)
        }
    }
    
    @SuppressLint("MissingPermission")
    override fun startAdvertising(data: ByteArray) {
        Log.d(TAG, "Starting advertising with data size: ${data.size} bytes")
        currentAdvertisingData = data
        
        // AdvertiserManager를 통해 광고 시작
        // 현재는 단순히 AdvertiserManager에 위임하지만,
        // 추후 Mesh 관련 데이터(TTL 등)를 광고 데이터에 포함시킬 수 있음
        try {
            advertiserManager.startAdvertisingWithData(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting advertising: ${e.message}")
        }
    }
    
    override fun stopAdvertising() {
        Log.d(TAG, "Stopping advertising")
        try {
            advertiserManager.stopAdvertising()
            currentAdvertisingData = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping advertising: ${e.message}")
        }
    }
    
    @SuppressLint("MissingPermission")
    override fun startScanning(callback: (ScanResult) -> Unit) {
        Log.d(TAG, "Starting scanning with HIGH power mode")
        if (isScanning) {
            Log.w(TAG, "Already scanning. Stopping current scan...")
            stopScanning()
        }
        
        isScanning = true
        scanCallback = MeshScanCallback(callback)
        
        // 초기 HIGH Power로 스캔 시작
        startHighPowerScan(callback)
    }
    
    override fun stopScanning() {
        Log.d(TAG, "Stopping scanning")
        if (!isScanning) {
            Log.w(TAG, "Not scanning. No need to stop.")
            return
        }
        
        // 스캔 중지
        try {
            scannerManager.stopScan()
            isScanning = false
            scanCallback = null
            
            // 모든 콜백 제거
            handler.removeCallbacksAndMessages(null)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan: ${e.message}")
        }
    }
    
    /**
     * HIGH Power 모드로 스캔 시작
     */
    private fun startHighPowerScan(callback: (ScanResult) -> Unit) {
        if (!isScanning) return
        
        Log.d(TAG, "Starting HIGH power scan for $HIGH_SCAN_PERIOD ms")
        try {
            // HIGH Power 모드로 스캐너 설정 및 스캔 시작
            scannerManager.startHighPowerScanning(scanCallback!!)
            
            // 일정 시간 후 LOW Power 모드로 전환
            handler.postDelayed({
                startLowPowerScan(callback)
            }, HIGH_SCAN_PERIOD)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting HIGH power scan: ${e.message}")
            // 오류 발생 시 LOW Power 모드로 폴백
            startLowPowerScan(callback)
        }
    }
    
    /**
     * LOW Power 모드로 스캔 시작
     */
    private fun startLowPowerScan(callback: (ScanResult) -> Unit) {
        if (!isScanning) return
        
        Log.d(TAG, "Starting LOW power scan for $LOW_SCAN_PERIOD ms")
        try {
            // LOW Power 모드로 스캐너 설정 및 스캔 시작
            scannerManager.startLowPowerScanning(scanCallback!!)
            
            // 일정 시간 후 HIGH Power 모드로 전환
            handler.postDelayed({
                startHighPowerScan(callback)
            }, LOW_SCAN_PERIOD)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting LOW power scan: ${e.message}")
            // 오류 발생 시 재시도
            handler.postDelayed({
                startHighPowerScan(callback)
            }, 5000) // 5초 후 재시도
        }
    }
} 