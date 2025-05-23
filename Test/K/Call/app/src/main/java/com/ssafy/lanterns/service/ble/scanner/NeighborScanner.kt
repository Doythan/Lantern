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
import com.ssafy.lanterns.config.BleConstants
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
    private var isIntervalScanningActive = false
    private var myNickname: String = ""
    private var myServerId: Long = -1L
    private var scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY

    // 플래그 비트 정의 (Advertiser와 동일하게)
    private const val EMERGENCY_FLAG_BIT: Byte = 0b00000001 // 0번 비트
    private const val CALLABLE_FLAG_BIT: Byte = 0b00000010  // 1번 비트

    val scannedDevicesMap = ConcurrentHashMap<String, ScannedDeviceData>()

    private const val SCAN_DURATION_MS = 3000L
    private const val SCAN_INTERVAL_MS = 4000L
    const val DEVICE_EXPIRATION_MS = 20000L

    data class ScannedDeviceData(
        val serverUserId: Long,
        val nickname: String,
        val advertisedOwnDepth: Int,
        var rssi: Int,
        var lastSeen: Long,
        val bleAddress: String,
        var isEmergency: Boolean = false,
        var profileImageNumber: Int = 0,
        var isCallable: Boolean = true
    )

    private data class ParsedAdPacket(
        val serverUserId: Long,
        val advertisedOwnDepth: Int,
        val nickname: String,
        val isEmergency: Boolean,
        val profileImageNumber: Int = 0,
        val isCallable: Boolean
    )

    fun init(activity: Activity) {
        currentActivityRef = WeakReference(activity)
        try {
            val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter
            bluetoothLeScanner = adapter?.bluetoothLeScanner

            Log.i(TAG, "Scanner 초기화: manager=${bluetoothManager != null}, adapter=${adapter != null}, enabled=${adapter?.isEnabled ?: false}, scanner=${bluetoothLeScanner != null}")

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

    fun setMyNickname(nickname: String) {
        myNickname = nickname
        Log.i(TAG, "내 닉네임 설정됨: '$nickname'")
    }

    fun setMyServerId(serverId: Long) {
        myServerId = serverId
        Log.i(TAG, "내 서버 ID 설정됨: $serverId")
    }

    fun startScanning() {
        val activity = currentActivityRef?.get() ?: run { Log.e(TAG, "Activity null, 스캔 불가"); return }

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
            init(activity)
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

        val currentAppFilter = ScanFilter.Builder()
            .setManufacturerData(BleConstants.LANTERN_MANUFACTURER_ID_MESSAGE, null)
            .build()

        val oldAppFilter = ScanFilter.Builder()
            .setManufacturerData(BleConstants.MANUFACTURER_ID_USER, null)
            .build()

        val scanFilters = listOf(currentAppFilter, oldAppFilter)

        scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY

        val scanSettings = ScanSettings.Builder()
            .setScanMode(scanMode)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0)
            .build()

        Log.i(TAG, "스캔 필터 설정: 제조사 ID(${BleConstants.LANTERN_MANUFACTURER_ID_MESSAGE} or ${BleConstants.MANUFACTURER_ID_USER}), " +
              "스캔 모드: ${getScanModeName(scanMode)}")

        currentScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { scanResult ->
                    val scanRecord = scanResult.scanRecord
                    val device = scanResult.device

                    if (scanRecord == null) {
                        Log.w(TAG, "[onScanResult] ScanRecord is null for device: ${device.address}")
                        return@let
                    }

                    var manufacturerData = scanRecord.getManufacturerSpecificData(BleConstants.LANTERN_MANUFACTURER_ID_MESSAGE)

                    if (manufacturerData == null) {
                        manufacturerData = scanRecord.getManufacturerSpecificData(BleConstants.MANUFACTURER_ID_USER)
                    }

                    if (manufacturerData != null) {
                        try {
                            val parsed = parseLanternPacket(manufacturerData)

                            if (parsed.serverUserId == myServerId) {
                                return@let
                            }

                            if (scanResult.rssi < -98) {
                                return@let
                            }

                            if (parsed.nickname == "ErrorParse") {
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
                                    profileImageNumber = parsed.profileImageNumber,
                                    isCallable = parsed.isCallable
                                ) ?: ScannedDeviceData(
                                    serverUserId = parsed.serverUserId,
                                    nickname = parsed.nickname,
                                    advertisedOwnDepth = parsed.advertisedOwnDepth,
                                    rssi = scanResult.rssi,
                                    lastSeen = System.currentTimeMillis(),
                                    bleAddress = device.address,
                                    isEmergency = parsed.isEmergency,
                                    profileImageNumber = parsed.profileImageNumber,
                                    isCallable = parsed.isCallable
                                )
                                if (existing == null) {
                                    Log.i(TAG, "새 기기 '${parsed.nickname}'(${parsed.serverUserId}) 발견됨. 주소: ${device.address}, 프로필 이미지: ${parsed.profileImageNumber}, 통화가능: ${parsed.isCallable}")
                                }
                                newData
                            }
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
                    ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "이미 스캔 중"
                    ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "앱 등록 실패"
                    ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE 스캔 미지원"
                    ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "내부 오류"
                    ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "하드웨어 리소스 부족"
                    6 /* ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY */ -> "스캔 너무 자주"
                    else -> "알 수 없는 스캔 오류 ($errorCode)"
                }
                Log.e(TAG, "스캔 실패: $errorMsg")
                isCurrentlyScanning = false
                if (isIntervalScanningActive) {
                    scheduleNextScan(scanFilters, scanSettings)
                }
            }
        }
        startIntervalScanning(scanFilters, scanSettings, currentScanCallback)
        scheduleCleanupTask()
    }

    private fun startIntervalScanning(scanFilters: List<ScanFilter>, scanSettings: ScanSettings, callback: ScanCallback?) {
        val activity = currentActivityRef?.get() ?: return
        if (callback == null) return

        isIntervalScanningActive = true
        Log.i(TAG, "간헐적 스캔 패턴 시작: ${SCAN_DURATION_MS}ms 스캔 / ${SCAN_INTERVAL_MS}ms 간격")

        try {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "스캔 시작 시도...")
                try {
                    bluetoothLeScanner?.startScan(scanFilters, scanSettings, callback)
                    isCurrentlyScanning = true
                    Log.i(TAG, "스캔 성공적으로 시작됨.")
                } catch (e: SecurityException) {
                    Log.e(TAG, "BLUETOOTH_SCAN 권한 예외: ${e.message}")
                    isCurrentlyScanning = false
                }

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
                if (isIntervalScanningActive) {
                    scheduleNextScan(scanFilters, scanSettings)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "스캔 시작 중 오류: ${e.message}", e)
            isCurrentlyScanning = false
            if (isIntervalScanningActive) {
                scheduleNextScan(scanFilters, scanSettings)
            }
        }
    }

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
            startIntervalScanning(scanFilters, scanSettings, currentScanCallback)
        }
    }

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
        isIntervalScanningActive = false
        handler.removeCallbacksAndMessages(null)
        val localCallback = currentScanCallback

        if (!isCurrentlyScanning || bluetoothLeScanner == null || localCallback == null) {
            Log.d(TAG, "스캔 중이 아니거나, 스캐너/콜백 null. 중지 스킵. isCurrentlyScanning=$isCurrentlyScanning, scannerNull=${bluetoothLeScanner == null}, callbackNull=${localCallback == null}")
            if (!isCurrentlyScanning) {
                currentScanCallback = null
            }
            isCurrentlyScanning = false
            return
        }
        Log.i(TAG, "스캔 중지 시도...")
        try {
            val activity = currentActivityRef?.get()
            if (activity != null && ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothLeScanner?.stopScan(localCallback)
                Log.i(TAG, "스캔 성공적으로 중지됨.")
            } else {
                Log.e(TAG, "BLUETOOTH_SCAN 권한 없음, 스캔 중지 불가능")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "스캔 중지 중 보안 예외: ${e.message}")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "스캔 중지 중 IllegalStateException: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "스캔 중지 중 알 수 없는 예외: ${e.message}")
        } finally {
            isCurrentlyScanning = false
            currentScanCallback = null
            Log.d(TAG, "스캔 중지 완료. isCurrentlyScanning=$isCurrentlyScanning, currentScanCallback is null.")
        }
    }

    fun release() {
        Log.i(TAG, "NeighborScanner release 호출됨.")
        stopScanning()
        currentActivityRef?.clear()
        currentActivityRef = null
        bluetoothLeScanner = null
        Log.i(TAG, "NeighborScanner 리소스 해제 완료.")
    }

    private fun parseLanternPacket(data: ByteArray): ParsedAdPacket {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        try {
            val isOldFormat = data.size < 9 ||
                    (data[0] != BleConstants.DATA_TYPE_LANTERN_V1 || data[1] != BleConstants.PROTOCOL_VERSION_V1)
            if (isOldFormat) {
                val dataString = String(data, StandardCharsets.UTF_8)
                Log.v(TAG, "이전 앱 형식 데이터 발견: '$dataString'")
                var nickname = dataString
                var serverId = 0L
                if (dataString.contains("#")) {
                    val parts = dataString.split("#", limit = 2)
                    nickname = parts[0]
                    if (parts.size > 1) {
                        serverId = try { parts[1].toLong() } catch (e: NumberFormatException) { 0L }
                    }
                }
                return ParsedAdPacket(serverId, 0, nickname, false, 0, true)
            }

            if (buffer.remaining() < 1) throw IllegalArgumentException("데이터 타입 읽기 부족")
            val dataType = buffer.get()
            if (buffer.remaining() < 1) throw IllegalArgumentException("버전 읽기 부족")
            val version = buffer.get()

            if (dataType != BleConstants.DATA_TYPE_LANTERN_V1 || version != BleConstants.PROTOCOL_VERSION_V1) {
                throw IllegalArgumentException("지원하지 않는 패킷 타입($dataType) 또는 버전($version)")
            }

            if (buffer.remaining() < BleConstants.SERVER_USER_ID_BYTES) throw IllegalArgumentException("UserID 읽기 부족")
            val serverUserId = buffer.int.toLong()

            if (buffer.remaining() < BleConstants.DEPTH_BYTES) throw IllegalArgumentException("Depth 읽기 부족")
            val advertisedOwnDepth = buffer.get().toInt() and 0xFF

            if (buffer.remaining() < 1) throw IllegalArgumentException("닉네임 길이 읽기 부족")
            val nicknameLength = buffer.get().toInt() and 0xFF

            if (nicknameLength < 0 || nicknameLength > BleConstants.MAX_NICKNAME_BYTES_ADV) {
                throw IllegalArgumentException("잘못된 Nickname 길이: $nicknameLength")
            }

            val nickname = if (nicknameLength > 0) {
                if (buffer.remaining() < nicknameLength) throw IllegalArgumentException("닉네임 읽기 부족")
                val nicknameBytes = ByteArray(nicknameLength)
                buffer.get(nicknameBytes)
                String(nicknameBytes, StandardCharsets.UTF_8)
            } else ""

            if (buffer.remaining() < 1) throw IllegalArgumentException("플래그 읽기 부족")
            val flags = buffer.get()
            val isEmergency = (flags.toInt() and EMERGENCY_FLAG_BIT.toInt()) != 0
            val isCallable = (flags.toInt() and CALLABLE_FLAG_BIT.toInt()) != 0

            val profileImageNumber = if (buffer.remaining() >= 1) {
                buffer.get().toInt() and 0xFF
            } else {
                0
            }

            Log.d(TAG, "Parsed Packet: sID=$serverUserId, Nick='$nickname', Depth=$advertisedOwnDepth, Emergency=$isEmergency, Callable=$isCallable, ProfileImg=$profileImageNumber")
            return ParsedAdPacket(serverUserId, advertisedOwnDepth, nickname, isEmergency, profileImageNumber, isCallable)

        } catch (e: Exception) {
            val dataHex = data.joinToString(" ") { String.format("%02X", it) }
            Log.e(TAG, "패킷 파싱 중 예외: ${e.message}, 데이터: $dataHex", e)
            return ParsedAdPacket(0L, 0, "ErrorParse", false, 0, false)
        }
    }

    private fun getScanModeName(scanMode: Int): String {
        return when (scanMode) {
            ScanSettings.SCAN_MODE_LOW_LATENCY -> "LOW_LATENCY"
            ScanSettings.SCAN_MODE_BALANCED -> "BALANCED"
            ScanSettings.SCAN_MODE_LOW_POWER -> "LOW_POWER"
            ScanSettings.SCAN_MODE_OPPORTUNISTIC -> "OPPORTUNISTIC"
            else -> "UNKNOWN ($scanMode)"
        }
    }

    private fun cleanupExpiredDevices() {
        val now = System.currentTimeMillis()
        val expiredKeys = scannedDevicesMap.filterValues { now - it.lastSeen > DEVICE_EXPIRATION_MS }.keys
        expiredKeys.forEach {
            scannedDevicesMap.remove(it)?.let {
                Log.d(TAG, "기기 제거 (시간 초과): '${it.nickname}'(${it.serverUserId})")
            }
        }
    }

    private fun scheduleCleanupTask() {
        handler.postDelayed({
            if (isCurrentlyScanning || isIntervalScanningActive) {
                cleanupExpiredDevices()
                scheduleCleanupTask()
            }
        }, 5000)
    }
}