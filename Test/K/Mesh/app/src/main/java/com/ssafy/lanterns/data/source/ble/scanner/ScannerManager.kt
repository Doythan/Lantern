package com.ssafy.lanterns.data.source.ble.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.ssafy.lanterns.data.source.ble.gatt.GattServerManager
import com.ssafy.lanterns.data.source.ble.advertiser.AdvertiserManager
import com.ssafy.lanterns.data.source.ble.mesh.MeshMessage
import com.ssafy.lanterns.data.source.ble.mesh.MessageType
import com.ssafy.lanterns.data.source.ble.mesh.MessageUtils
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.Inflater

/**
 * BLE 스캔을 통한 메시지 수신을 관리하는 클래스
 * 수신된 메시지의 TTL 감소, 중복 제거, 재전파 기능 제공
 */
class ScannerManager(
    private val context: Context,
    private val onMessageReceived: (MeshMessage) -> Unit,
    private val advertiserManager: AdvertiserManager
) {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var mScanCallback: ScanCallback? = null
    private val isScanning = AtomicBoolean(false)
    private val TAG = "ScannerManager"
    
    // 서비스 UUID - 메쉬 네트워크 식별자
    private val serviceUUID = ParcelUuid(UUID.fromString("4d61-72b0-4e65-b208-6adff42f5624"))
    
    // 디바이스 주소
    private var deviceAddress: String? = null
    
    // 최근 수신 메시지 캐시 (중복 체크용)
    private val recentMessages = ArrayList<Long>()
    
    // 최대 캐시 크기
    private val MAX_RECENT_MESSAGES = 100
    
    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BluetoothLeScanner is not available.")
        }
        Log.d(TAG, "ScannerManager initialized.")
    }

    /**
     * 디바이스 주소 설정
     * @param address 디바이스 주소
     */
    fun setDeviceAddress(address: String) {
        this.deviceAddress = address
    }

    /**
     * 스캔 시작
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (isScanning.get()) {
            Log.d(TAG, "이미 스캔 중입니다")
            return
        }
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BluetoothLeScanner not initialized. Cannot start scanning.")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission denied: BLUETOOTH_SCAN. Cannot start scanning.")
            return
        }

        val SERVICE_UUID = serviceUUID

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(serviceUUID)
            .build()

        val scanFilters = listOf(scanFilter)

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, defaultScanCallback)
            mScanCallback = defaultScanCallback
            isScanning.set(true)
            Log.i(TAG, "Scanning started for service UUID: $SERVICE_UUID")
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting scan", e)
            isScanning.set(false)
        }
    }

    /**
     * HIGH Power 모드로 스캔 시작
     * 짧은 시간 동안 빠르게 주변 기기를 스캔할 때 사용 (배터리 소모가 큼)
     */
    @SuppressLint("MissingPermission")
    fun startHighPowerScanning(callback: ScanCallback) {
        mScanCallback = callback
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BluetoothLeScanner not initialized. Cannot start HIGH power scanning.")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission denied: BLUETOOTH_SCAN. Cannot start HIGH power scanning.")
            return
        }

        val SERVICE_UUID = serviceUUID

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(SERVICE_UUID)
            .build()

        val scanFilters = listOf(scanFilter)

        // HIGH Power 모드 설정
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // 가장 빠른 스캔 모드
            .setReportDelay(0) // 결과 즉시 보고
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT) // 가능한 많은 결과 반환
            .build()

        try {
            // 기존 스캔 중지
            if (isScanning.get()) {
                bluetoothLeScanner?.stopScan(mScanCallback)
            }
            
            // 새 스캔 시작
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, callback)
            isScanning.set(true)
            Log.i(TAG, "HIGH power scanning started for service UUID: $SERVICE_UUID")
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting HIGH power scan", e)
            isScanning.set(false)
        }
    }

    /**
     * LOW Power 모드로 스캔 시작
     * 긴 시간 동안 배터리를 절약하면서 스캔할 때 사용
     */
    @SuppressLint("MissingPermission")
    fun startLowPowerScanning(callback: ScanCallback) {
        mScanCallback = callback
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BluetoothLeScanner not initialized. Cannot start LOW power scanning.")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission denied: BLUETOOTH_SCAN. Cannot start LOW power scanning.")
            return
        }

        val SERVICE_UUID = serviceUUID

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(SERVICE_UUID)
            .build()

        val scanFilters = listOf(scanFilter)

        // LOW Power 모드 설정
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER) // 저전력 스캔 모드
            .setReportDelay(1000L) // 결과를 1초마다 배치로 보고
            .build()

        try {
            // 기존 스캔 중지
            if (isScanning.get()) {
                bluetoothLeScanner?.stopScan(mScanCallback)
            }
            
            // 새 스캔 시작
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, callback)
            isScanning.set(true)
            Log.i(TAG, "LOW power scanning started for service UUID: $SERVICE_UUID")
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting LOW power scan", e)
            isScanning.set(false)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning.get() || bluetoothLeScanner == null || mScanCallback == null) {
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission denied: BLUETOOTH_SCAN. Cannot stop scanning.")
            return
        }

        try {
            bluetoothLeScanner?.stopScan(mScanCallback)
            Log.i(TAG, "Scanning stopped.")
        } catch (e: Exception) {
            Log.e(TAG, "Exception stopping scan", e)
        }
        isScanning.set(false)
        mScanCallback = null
    }

    /**
     * 스캔 콜백
     */
    private val defaultScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            try {
                // 스캔된 기기 정보 로그
                val device = result.device
                Log.d(TAG, "기기 발견: ${device.address}")
                
                // 광고 데이터 추출
                val scanRecord = result.scanRecord ?: return
                val serviceData = scanRecord.getServiceData(serviceUUID) ?: return
                
                // 압축 해제
                val decompressedData = decompressMessage(serviceData)
                
                // 메시지 파싱
                val message = MeshMessage.fromBytes(decompressedData) ?: return
                
                // 자신이 보낸 메시지는 무시
                if (message.sender == deviceAddress) {
                    Log.d(TAG, "자신이 보낸 메시지 무시: seq=${message.sequenceNumber}")
                    return
                }
                
                // 중복 메시지 확인
                if (isDuplicateMessage(message.sequenceNumber.toLong())) {
                    Log.d(TAG, "중복 메시지 무시: seq=${message.sequenceNumber}")
                    return
                }
                
                // 메시지 처리
                onMessageReceived(message)
                
                // TTL이 남아있으면 재전파
                if (message.ttl > 0) {
                    val forwardMessage = message.copy(ttl = message.ttl - 1)
                    advertiserManager.startAdvertising(forwardMessage)
                    Log.d(TAG, "메시지 재전파: ttl=${forwardMessage.ttl}, seq=${forwardMessage.sequenceNumber}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "스캔 결과 처리 실패", e)
            }
        }
        
        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { onScanResult(0, it) }
        }
        
        override fun onScanFailed(errorCode: Int) {
            isScanning.set(false)
            
            val errorMessage = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "이미 스캔 중"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "앱 등록 실패"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "기능 미지원"
                SCAN_FAILED_INTERNAL_ERROR -> "내부 오류"
                else -> "알 수 없는 오류 $errorCode"
            }
            
            Log.e(TAG, "스캔 실패: $errorMessage")
        }
    }

    /**
     * 블루투스 디바이스에서 받은 메시지 압축 해제
     */
    private fun decompressMessage(compressedData: ByteArray): ByteArray {
        val inflater = Inflater()
        val outputBuffer = ByteArray(8192) // 충분히 큰 버퍼
        
        return try {
            inflater.setInput(compressedData)
            val resultLength = inflater.inflate(outputBuffer)
            inflater.end()
            
            outputBuffer.copyOfRange(0, resultLength)
        } catch (e: Exception) {
            Log.e(TAG, "압축 해제 실패", e)
            compressedData // 압축 해제 실패 시 원본 반환
        }
    }
    
    /**
     * 중복 메시지 확인
     * @param messageId 메시지 ID
     * @return 중복 여부
     */
    private fun isDuplicateMessage(messageId: Long): Boolean {
        // 최근 메시지 캐시 확인
        val result = recentMessages.contains(messageId)
        
        // 캐시에 추가
        if (!result) {
            recentMessages.add(messageId)
            
            // 캐시 크기 관리
            if (recentMessages.size > MAX_RECENT_MESSAGES) {
                recentMessages.removeAt(0)
            }
        }
        
        return result
    }
}