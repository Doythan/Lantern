package com.example.blemodule.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

/**
 * BLE 스캐너 클래스
 * 주변 BLE 기기를 스캔하고 연결을 시도
 */
class BleScanner(private val context: Context, private val bleManager: BleManager) {
    private val TAG = "BleScanner"
    
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())
    
    // 스캔 주기 (밀리초)
    private val SCAN_PERIOD: Long = 10000
    
    // 이미 발견된 기기 목록
    private val discoveredDevices = mutableSetOf<String>()
    
    // BLE 스캔 시작
    fun startScanning(bluetoothAdapter: BluetoothAdapter, serviceUuid: UUID) {
        if (scanning) {
            return
        }
        
        Log.d(TAG, "BLE 스캔 시작")
        
        // 이전에 발견된 기기 목록 초기화
        discoveredDevices.clear()
        
        // BLE 스캐너 가져오기
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        
        // 스캔 필터 설정 (특정 서비스 UUID를 가진 기기만 검색)
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUuid))
            .build()
        
        // 스캔 설정
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        // 스캔 시작
        scanning = true
        bluetoothLeScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
        
        // 일정 시간 후 스캔 중지
        handler.postDelayed({
            stopScanning()
            // 일정 시간 후 다시 스캔 시작
            handler.postDelayed({
                startScanning(bluetoothAdapter, serviceUuid)
            }, 5000)
        }, SCAN_PERIOD)
    }
    
    // BLE 스캔 중지
    fun stopScanning() {
        if (!scanning) {
            return
        }
        
        Log.d(TAG, "BLE 스캔 중지")
        
        scanning = false
        bluetoothLeScanner?.stopScan(scanCallback)
        
        // 지연된 작업 제거
        handler.removeCallbacksAndMessages(null)
    }
    
    // 스캔 콜백
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val address = device.address
            
            // 이미 발견된 기기인 경우 무시
            if (discoveredDevices.contains(address)) {
                return
            }
            
            Log.d(TAG, "기기 발견: ${device.address}, ${device.name}")
            
            // 발견된 기기 목록에 추가
            discoveredDevices.add(address)
            
            // 기기에 연결
            connectToDevice(device)
        }
        
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (result in results) {
                onScanResult(0, result)
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "스캔 실패: $errorCode")
        }
    }
    
    // 기기에 연결
    private fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "기기에 연결 시도: ${device.address}")
        
        // BleClient를 통해 기기에 연결
        val bleClient = BleClient(context, bleManager)
        bleClient.connectToDevice(device)
    }
}
