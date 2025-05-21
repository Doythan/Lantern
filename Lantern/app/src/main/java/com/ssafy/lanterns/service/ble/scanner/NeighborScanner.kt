package com.ssafy.lanterns.service.ble.scanner

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.util.isNotEmpty
import com.ssafy.lanterns.config.BleConstants
import com.ssafy.lanterns.config.NeighborDiscoveryConstants
import com.ssafy.lanterns.utils.SignalStrengthManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.lang.ref.WeakReference

object NeighborScanner {
    private const val TAG = "LANT_Scan_v3"
    private var bluetoothLeScanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var currentScanCallback: ScanCallback? = null
    private var currentActivityRef: WeakReference<Activity>? = null
    @Volatile private var isCurrentlyScanning = false
    private val handler = Handler(Looper.getMainLooper())
    private var isIntervalScanningActive = false // 간헐적 스캔 활성화 여부
    private var myNickname: String = "" // 내 닉네임 저장 변수
    private var myServerId: Long = -1L // 내 서버 ID 저장 변수
    private var scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY // 스캔 모드 변수로 변경

    val scannedDevicesMap = ConcurrentHashMap<String, ScannedDeviceData>()

    // 간헐적 스캔 상수 업데이트
    private const val SCAN_DURATION_MS = 3000L // 3초간 스캔
    private const val SCAN_INTERVAL_MS = 4000L // 4초 간격 (3초 스캔 + 1초 대기)
    
    // 스캔된 기기 정보 만료 시간 - 20초로 설정 (원래 20초)
    const val DEVICE_EXPIRATION_MS = 20000L

    data class ScannedDeviceData(
        val serverUserId: Long,
        val nickname: String,
        val advertisedOwnDepth: Int, // 상대방이 광고한 자신의 Depth
        var rssi: Int,
        var lastSeen: Long,
        val bleAddress: String,
        var isEmergency: Byte = 0,
        var profileImageNumber: Int = 0 // 프로필 이미지 번호 추가
    )

    private data class ParsedAdPacket(
        val serverUserId: Long,
        val advertisedOwnDepth: Int,
        val nickname: String,
        val isEmergency: Byte,
        val profileImageNumber: Int = 0 // 프로필 이미지 번호 추가
    )

    fun init(activity: Activity) {
        currentActivityRef = WeakReference(activity)
        try {
            val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter
            bluetoothLeScanner = adapter?.bluetoothLeScanner
            
            Log.i(TAG, "Scanner 초기화: manager=${bluetoothManager != null}, adapter=${adapter != null}, enabled=${adapter?.isEnabled ?: false}, scanner=${bluetoothLeScanner != null}")
            
            // 권한 확인 로직 추가
            val hasBluetoothScanPermission = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            
            val hasBluetoothConnectPermission = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            
            val hasLocationPermission = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            Log.i(TAG, "권한 상태: BLUETOOTH_SCAN=$hasBluetoothScanPermission, " +
                    "BLUETOOTH_CONNECT=$hasBluetoothConnectPermission, " +
                    "ACCESS_FINE_LOCATION=$hasLocationPermission")
            
            if (adapter?.isEnabled == false) {
                Log.e(TAG, "블루투스가 비활성화되어 있습니다. 스캔을 시작할 수 없습니다.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scanner 초기화 중 오류: ${e.message}")
            bluetoothLeScanner = null
        }
        if (bluetoothLeScanner == null) Log.e(TAG, "BLE Scanner 초기화 실패 (또는 지원 안함)")
    }

    /**
     * 내 닉네임을 설정합니다.
     * 자신의 광고를 필터링할 때 사용됩니다.
     */
    fun setMyNickname(nickname: String) {
        myNickname = nickname
        Log.i(TAG, "내 닉네임 설정됨: '$nickname'")
    }
    
    /**
     * 내 서버 ID를 설정합니다.
     * 자신의 광고를 더 정확히 필터링할 때 사용됩니다.
     */
    fun setMyServerId(serverId: Long) {
        myServerId = serverId
        Log.i(TAG, "내 서버 ID 설정됨: $serverId")
    }

    fun startScanning() {
        val activity = currentActivityRef?.get() ?: run { Log.e(TAG, "Activity null, 스캔 불가"); return }
        
        // 블루투스 상태 확인
        val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        
        if (adapter == null) {
            Log.e(TAG, "블루투스 어댑터가 null입니다. 기기가 블루투스를 지원하지 않을 수 있습니다.")
            return
        }
        
        if (!adapter.isEnabled) {
            Log.e(TAG, "블루투스가 비활성화되어 있습니다. 스캔을 시작할 수 없습니다.")
            return
        }
        
        if (bluetoothLeScanner == null) {
            Log.i(TAG, "Scanner null, 스캔 불가. 초기화 재시도.")
            init(activity) // 초기화 재시도
            if (bluetoothLeScanner == null) {
                Log.e(TAG, "Scanner 재초기화 실패, 스캔을 시작할 수 없음")
                return
            }
        }

        if (isCurrentlyScanning && currentScanCallback != null) {
            Log.d(TAG, "이미 스캔 중. 지금 스캔은 무시합니다.")
            return
        }

        Log.i(TAG, "BLE 스캔 시작 - 초기 세팅")
        
        SignalStrengthManager.clearAllHistory() // RSSI 필터 기록 초기화

        // 스캔 필터 개선 - 3가지 필터 방식 시도
        // 1. 현재 앱 필터
        val currentAppFilter = ScanFilter.Builder()
            .setManufacturerData(BleConstants.LANTERN_MANUFACTURER_ID_MESSAGE, null)
            .build()
        
        // 2. 이전 앱 필터
        val oldAppFilter = ScanFilter.Builder()
            .setManufacturerData(BleConstants.MANUFACTURER_ID_USER, null)
            .build()
        
        // 불필요한 필터 제거
        val scanFilters = listOf(currentAppFilter, oldAppFilter)
        
        // 배터리 절약을 위해 균형 모드 사용
        scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(scanMode)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) // 모든 매치 콜백
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE) // 적극적인 매치 모드
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT) // 최대 광고 수 매치
            .setReportDelay(0) // 딜레이 없이 즉시 보고
            .build()
        
        Log.i(TAG, "스캔 필터 설정: 제조사 ID(${BleConstants.LANTERN_MANUFACTURER_ID_MESSAGE}), 필터 완화됨, " +
              "스캔 모드: ${getScanModeName(scanMode)}")

        currentScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                // 디버그 레벨 로그는 제거하고 필요한 정보만 유지
                result?.let { scanResult ->
                    val scanRecord = scanResult.scanRecord
                    val device = scanResult.device
                    
                    if (scanRecord == null) {
                        Log.w(TAG, "[onScanResult] ScanRecord is null for device: ${device.address}")
                        return@let
                    }

                    // 간소화: 제조사 데이터 확인
                    var manufacturerData = scanRecord.getManufacturerSpecificData(BleConstants.LANTERN_MANUFACTURER_ID_MESSAGE)
                    var isFromOldApp = false
                    
                    // 없으면 이전 앱의 ID도 확인
                    if (manufacturerData == null) {
                        manufacturerData = scanRecord.getManufacturerSpecificData(BleConstants.MANUFACTURER_ID_USER)
                        if (manufacturerData != null) {
                            isFromOldApp = true
                        }
                    }
                    
                    if (manufacturerData != null) {
                        try {
                            val parsed = parseLanternPacket(manufacturerData)
                            
                            // 자신의 광고는 무시
                            if (parsed.serverUserId == myServerId) {
                                return@let
                            }
                            
                            // RSSI 값 필터링 (-98 이하는 무시)
                            if (scanResult.rssi < -98) {
                                return@let
                            }
                            
                            if (parsed.nickname == "ErrorParse") {
                                // ErrorParse 기기는 맵에 추가하지 않음
                                Log.d(TAG, "ErrorParse 기기 감지됨 - 맵에 추가하지 않음")
                                return@let
                            }
                            
                            scannedDevicesMap.compute(device.address) { _, existing ->
                                val newData = existing?.copy(
                                    serverUserId = parsed.serverUserId,
                                    nickname = parsed.nickname,
                                    advertisedOwnDepth = parsed.advertisedOwnDepth,
                                    rssi = scanResult.rssi,
                                    lastSeen = System.currentTimeMillis(),
                                    isEmergency = parsed.isEmergency,
                                    profileImageNumber = parsed.profileImageNumber // 프로필 이미지 번호 추가
                                ) ?: ScannedDeviceData(
                                    serverUserId = parsed.serverUserId,
                                    nickname = parsed.nickname,
                                    advertisedOwnDepth = parsed.advertisedOwnDepth,
                                    rssi = scanResult.rssi,
                                    lastSeen = System.currentTimeMillis(),
                                    bleAddress = device.address,
                                    isEmergency = parsed.isEmergency,
                                    profileImageNumber = parsed.profileImageNumber // 프로필 이미지 번호 추가
                                )
                                if (existing == null) {
                                    Log.i(TAG, "새 기기 '${parsed.nickname}'(${parsed.serverUserId}) 발견됨. 주소: ${device.address}, 프로필 이미지: ${parsed.profileImageNumber}")
                                }
                                newData
                            }
                            
                            // 오래된 기기 정보 제거 (10초 이상 업데이트 없는 경우)
                            cleanupExpiredDevices()
                        } catch (e: Exception) {
                            Log.e(TAG, "패킷 파싱 오류 (${device.address}): ${e.message}")
                        }
                    }
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.let {
                    Log.d(TAG, "[onBatchScanResults] Received batch of ${it.size} results.")
                    it.forEach { result -> onScanResult(0, result) }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                val errorMsg = when (errorCode) {
                    ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "이미 스캔 중 (SCAN_FAILED_ALREADY_STARTED)"
                    ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "앱 등록 실패 (SCAN_FAILED_APPLICATION_REGISTRATION_FAILED)"
                    ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "기기에서 BLE 스캔을 지원하지 않음 (SCAN_FAILED_FEATURE_UNSUPPORTED)"
                    ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "내부 오류 (SCAN_FAILED_INTERNAL_ERROR)"
                    ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "하드웨어 리소스 부족 (SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES)"
                    6 /* ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY */ -> "스캐닝 너무 자주 시작됨 (SCAN_FAILED_SCANNING_TOO_FREQUENTLY)"
                    else -> "알 수 없는 스캔 오류 코드 ($errorCode)"
                }
                Log.e(TAG, "스캔 실패, 코드: $errorCode, 원인: $errorMsg")
                isCurrentlyScanning = false
                
                // 간헐적 스캔 중인 경우는 재시도를 위해 스케줄링 유지
                if (isIntervalScanningActive) {
                    scheduleNextScan(scanFilters, scanSettings)
                }
            }
        }

        // 간헐적 스캔 시작
        startIntervalScanning(scanFilters, scanSettings, currentScanCallback)
        
        // 주기적으로 오래된 기기 정보 제거
        scheduleCleanupTask()
    }

    /**
     * 간헐적 스캔 패턴을 시작합니다.
     * 3초 동안 스캔한 후 0.5초 대기하고 다시 스캔합니다.
     */
    private fun startIntervalScanning(scanFilters: List<ScanFilter>, scanSettings: ScanSettings, callback: ScanCallback?) {
        val activity = currentActivityRef?.get() ?: return
        
        if (callback == null) return
        isIntervalScanningActive = true
        Log.i(TAG, "간헐적 스캔 패턴 시작: ${SCAN_DURATION_MS}ms 스캔 / ${SCAN_INTERVAL_MS}ms 간격")
        
        // 실제 스캔 시작
        try {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "스캔 시작 시도...")
                try {
                    bluetoothLeScanner?.startScan(scanFilters, scanSettings, callback)
                    isCurrentlyScanning = true
                    Log.i(TAG, "스캔 성공적으로 시작됨 (또는 시작 명령 전달됨).")
                } catch (e: SecurityException) {
                    Log.e(TAG, "BLUETOOTH_SCAN 권한 예외 발생: ${e.message}")
                    isCurrentlyScanning = false
                }
                
                // 스캔 지속 시간 후 스캔 중지 및 다음 스캔 일정 예약
                handler.postDelayed({
                    if (isCurrentlyScanning && isIntervalScanningActive) {
                        stopScanningInternal()
                        scheduleNextScan(scanFilters, scanSettings)
                    }
                }, SCAN_DURATION_MS)
            } else {
                Log.e(TAG, "BLUETOOTH_SCAN 권한 없음. 스캔 시작 불가.")
                callback.onScanFailed(ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED)
                isCurrentlyScanning = false
                
                // 다음 시도 스케줄링
                if (isIntervalScanningActive) {
                    scheduleNextScan(scanFilters, scanSettings)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "스캔 시작 중 오류: ${e.message}", e)
            isCurrentlyScanning = false
            
            // 다음 시도 스케줄링
            if (isIntervalScanningActive) {
                scheduleNextScan(scanFilters, scanSettings)
            }
        }
    }
    
    /**
     * 다음 스캔을 스케줄링합니다.
     */
    private fun scheduleNextScan(scanFilters: List<ScanFilter>, scanSettings: ScanSettings) {
        if (!isIntervalScanningActive) return
        
        val remainingWaitTime = SCAN_INTERVAL_MS - SCAN_DURATION_MS
        if (remainingWaitTime > 0) {
            handler.postDelayed({
                if (isIntervalScanningActive) {
                    startIntervalScanning(scanFilters, scanSettings, currentScanCallback)
                }
            }, remainingWaitTime)
        } else {
            // 즉시 다음 스캔 시작
            startIntervalScanning(scanFilters, scanSettings, currentScanCallback)
        }
    }
    
    /**
     * 내부적으로 스캔을 중지합니다 (간헐적 스캔 패턴 중에 사용).
     */
    private fun stopScanningInternal() {
        val localCallback = currentScanCallback

        if (!isCurrentlyScanning || bluetoothLeScanner == null || localCallback == null) {
            return
        }
        
        try {
            bluetoothLeScanner?.stopScan(localCallback)
            Log.d(TAG, "스캔 중지 (간헐적 스캔 사이클)")
        } catch (e: SecurityException) {
            Log.e(TAG, "스캔 중지 중 권한 오류: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "스캔 중지 중 오류: ${e.message}")
        } finally {
            isCurrentlyScanning = false
        }
    }

    fun stopScanning() {
        isIntervalScanningActive = false // 간헐적 스캔 플래그 비활성화
        handler.removeCallbacksAndMessages(null) // 모든 스케줄링 취소
        
        val localCallback = currentScanCallback

        if (!isCurrentlyScanning || bluetoothLeScanner == null || localCallback == null) {
            Log.d(TAG, "스캔 중이 아니거나, 스캐너/콜백이 null이므로 중지 작업 스킵. isCurrentlyScanning=$isCurrentlyScanning, scannerNull=${bluetoothLeScanner == null}, callbackNull=${localCallback == null}")
            if (!isCurrentlyScanning) { // 스캔 중이 아니라고 판단될 때
                currentScanCallback = null // 콜백 참조 확실히 제거
            }
            isCurrentlyScanning = false // 상태 확실히 false로
            return
        }
        Log.i(TAG, "스캔 중지 시도...")
        try {
            // BLUETOOTH_SCAN 권한 체크 추가
            val activity = currentActivityRef?.get()
            if (activity != null && ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothLeScanner?.stopScan(localCallback)
                Log.i(TAG, "스캔 성공적으로 중지됨 (또는 중지 명령 전달됨).")
            } else {
                Log.e(TAG, "BLUETOOTH_SCAN 권한 없음, 스캔 중지 불가능")
            }
        } catch (e: SecurityException) { 
            Log.e(TAG, "스캔 중지 중 보안 예외 (권한 문제 가능성): ${e.message}")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "스캔 중지 중 IllegalStateException (블루투스 비활성화 또는 어댑터 문제): ${e.message}")
        } catch (e: Exception) { 
            Log.e(TAG, "스캔 중지 중 알 수 없는 예외: ${e.message}")
        } finally {
            isCurrentlyScanning = false
            currentScanCallback = null 
            Log.d(TAG, "스캔 중지 완료. isCurrentlyScanning=$isCurrentlyScanning, currentScanCallback is null.")
        }
    }

    fun release() {
        Log.i(TAG, "NeighborScanner release 호출됨. 스캔 중지 및 리소스 해제 시도.")
        stopScanning() 
        currentActivityRef?.clear() 
        currentActivityRef = null
        bluetoothLeScanner = null 
        Log.i(TAG, "NeighborScanner 리소스 해제 완료.")
    }

    private fun parseLanternPacket(data: ByteArray): ParsedAdPacket {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        
        try {
            // 이전 앱 형식인지 확인 (단순 닉네임#ID 형식)
            val isOldFormat = data.size < 9 ||
                    (data[0] != BleConstants.DATA_TYPE_LANTERN_V1 || data[1] != BleConstants.PROTOCOL_VERSION_V1)
            if (isOldFormat) {
                // 이전 앱 형식 처리 (닉네임#ID)
                val dataString = String(data, StandardCharsets.UTF_8)
                Log.v(TAG, "이전 앱 형식 데이터 발견: '$dataString'")
                var nickname = dataString
                var serverId = 0L
                // 예외 처리 강화: #이 없거나 닉네임만 있는 경우도 처리
                if (dataString.contains("#")) {
                    val parts = dataString.split("#", limit = 2)
                    if (parts.size == 2) {
                        nickname = parts[0]
                        if (parts.size > 1) {
                            serverId = try { parts[1].toLong() } catch (e: NumberFormatException) { 0L }
                        }
                    }
                }
                // #이 없는 경우는 닉네임만 있다고 가정
                return ParsedAdPacket(serverId, 0, nickname, 0.toByte(), 0) // 이전 형식에서는 프로필 이미지 번호를 0으로 설정
            }
            
            // 현재 앱 형식 처리
            if (buffer.remaining() < 1) throw IllegalArgumentException("데이터 타입 읽기 위한 버퍼 부족 (1 byte)")
            val dataType = buffer.get()
            
            if (buffer.remaining() < 1) throw IllegalArgumentException("버전 읽기 위한 버퍼 부족 (1 byte)")
            val version = buffer.get()
            
            if (dataType != BleConstants.DATA_TYPE_LANTERN_V1 || version != BleConstants.PROTOCOL_VERSION_V1) {
                throw IllegalArgumentException("지원하지 않는 패킷 타입($dataType) 또는 버전($version). Expected: Type=${BleConstants.DATA_TYPE_LANTERN_V1}, Ver=${BleConstants.PROTOCOL_VERSION_V1}")
            }

            if (buffer.remaining() < BleConstants.SERVER_USER_ID_BYTES) {
                throw IllegalArgumentException("UserID 읽기 위한 버퍼 부족 (${BleConstants.SERVER_USER_ID_BYTES} bytes)")
            }
            val serverUserId = buffer.int.toLong()
            
            if (buffer.remaining() < BleConstants.DEPTH_BYTES) {
                throw IllegalArgumentException("Depth 읽기 위한 버퍼 부족 (${BleConstants.DEPTH_BYTES} byte)")
            }
            val advertisedOwnDepth = buffer.get().toInt() and 0xFF


            if (buffer.remaining() < 1) throw IllegalArgumentException("닉네임 길이 읽기 위한 버퍼 부족 (1 byte)")
            val nicknameLength = buffer.get().toInt() and 0xFF

            if (nicknameLength > BleConstants.MAX_NICKNAME_BYTES_ADV) { // 0도 유효한 길이 (닉네임 없음)
                throw IllegalArgumentException("잘못된 Nickname 길이: $nicknameLength. 허용 범위: 0-${BleConstants.MAX_NICKNAME_BYTES_ADV}")
            }

            val nickname = if (nicknameLength > 0) {
                if (buffer.remaining() < nicknameLength) {
                    throw IllegalArgumentException("닉네임 읽기 위한 버퍼 부족 ($nicknameLength bytes)")
                }
                val nicknameBytes = ByteArray(nicknameLength)
                buffer.get(nicknameBytes)
                String(nicknameBytes, StandardCharsets.UTF_8)
            } else ""

            if (buffer.remaining() < 1) {
                throw IllegalArgumentException("긴급 플래그 읽기 위한 버퍼 부족 (1 byte)")
            }
            val isEmergencyFlag = buffer.get()
            
            // 프로필 이미지 번호 읽기 (남은 버퍼가 있는 경우에만)
            val profileImageNumber = if (buffer.remaining() >= 1) {
                buffer.get().toInt() and 0xFF
            } else {
                0 // 기본값 (이전 버전 호환성)
            }
            
            Log.d(TAG, "Parsed Packet: sID=$serverUserId, Nick='$nickname', Depth=$advertisedOwnDepth, Emergency=$isEmergencyFlag, ProfileImg=$profileImageNumber")

            return ParsedAdPacket(serverUserId, advertisedOwnDepth, nickname, isEmergencyFlag, profileImageNumber)
        } catch (e: Exception) {
            // 예외 발생 시 원본 데이터 로깅
            val dataHex = data.joinToString(" ") { String.format("%02X", it) }
            Log.e(TAG, "패킷 파싱 중 예외: ${e.message}, 원본 데이터: $dataHex", e)
            // errorparse(에러파스찾았다요놈)
            return ParsedAdPacket(0L, 0, "ErrorParse", 0.toByte(), 0)
        }
    }

    // Helper function to get scan mode name for logs
    private fun getScanModeName(scanMode: Int): String {
        return when (scanMode) {
            ScanSettings.SCAN_MODE_LOW_LATENCY -> "LOW_LATENCY (고성능)"
            ScanSettings.SCAN_MODE_BALANCED -> "BALANCED (균형)"
            ScanSettings.SCAN_MODE_LOW_POWER -> "LOW_POWER (저전력)"
            ScanSettings.SCAN_MODE_OPPORTUNISTIC -> "OPPORTUNISTIC (기회적)"
            else -> "UNKNOWN ($scanMode)"
        }
    }
    
    /**
     * 오래된 기기 정보를 제거합니다 (10초 이상 업데이트 없는 경우)
     */
    private fun cleanupExpiredDevices() {
        val now = System.currentTimeMillis()
        val expiredDevices = scannedDevicesMap.entries.filter { 
            now - it.value.lastSeen > DEVICE_EXPIRATION_MS 
        }.map { it.key }
        
        for (address in expiredDevices) {
            scannedDevicesMap.remove(address)?.let { device ->
                Log.d(TAG, "기기 제거됨 (시간 초과): '${device.nickname}'(${device.serverUserId})")
            }
        }
    }
    
    /**
     * 주기적으로 오래된 기기 정보를 제거하는 태스크를 스케줄링합니다
     */
    private fun scheduleCleanupTask() {
        handler.postDelayed({
            if (isCurrentlyScanning) {
                cleanupExpiredDevices()
                scheduleCleanupTask()
            }
        }, 5000) // 5초마다 정리
    }
}