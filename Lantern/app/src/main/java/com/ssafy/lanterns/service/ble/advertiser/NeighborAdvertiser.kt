package com.ssafy.lanterns.service.ble.advertiser

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
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
import java.lang.ref.WeakReference

object NeighborAdvertiser {
    private const val TAG = "LANT_Adv_Final"
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var currentAdvertiseCallback: AdvertiseCallback? = null
    private var currentActivityRef: WeakReference<Activity>? = null
    @Volatile private var isCurrentlyAdvertising = false
    
    // 광고 재시도 관리용 Handler
    private val handler = Handler(Looper.getMainLooper())
    private var pendingRetryRunnable: Runnable? = null

    fun init(activity: Activity) {
        currentActivityRef = WeakReference(activity)
        try {
            val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter
            bluetoothLeAdvertiser = adapter?.bluetoothLeAdvertiser
            
            // 상세 로그 추가
            Log.i(TAG, "Advertiser 초기화: manager=${bluetoothManager != null}, adapter=${adapter != null}, enabled=${adapter?.isEnabled ?: false}, advertiser=${bluetoothLeAdvertiser != null}")
            
            if (adapter?.isEnabled == false) {
                Log.e(TAG, "블루투스가 비활성화되어 있습니다. 광고를 시작할 수 없습니다.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Advertiser 초기화 중 오류: ${e.message}")
            bluetoothLeAdvertiser = null
        }
        if (bluetoothLeAdvertiser == null) Log.e(TAG, "BLE Advertiser 초기화 실패 (또는 지원 안함)")
    }

    fun startAdvertising(serverUserId: Long, processedNickname: String, currentOwnAdvertisedDepth: Int) {
        val activity = currentActivityRef?.get() ?: run { 
            Log.e(TAG, "Activity null, 광고 불가")
            return
        }
        
        val advertiser = bluetoothLeAdvertiser ?: run { 
            Log.e(TAG, "Advertiser null, 광고 불가")
            init(activity) // 초기화 재시도
            if (bluetoothLeAdvertiser == null) {
                Log.e(TAG, "Advertiser 재초기화 실패, 광고를 시작할 수 없음")
                return
            } 
            bluetoothLeAdvertiser!!
        }

        if (isCurrentlyAdvertising) {
            stopAdvertisingInternal(advertiser) // 새 정보로 광고하려면 이전 광고 중지
        }

        // 광고 설정 - 적절한 균형으로 설정
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED) // 균형적 모드(~250ms)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM) // 중간 전송 파워
            .setConnectable(false) // 연결 불필요
            .setTimeout(0) // 타임아웃 없음 (계속 광고)
            .build()

        val lanternPacket = buildLanternPacket(serverUserId, processedNickname, currentOwnAdvertisedDepth)
        if (lanternPacket == null) { 
            Log.e(TAG, "패킷 생성 실패") 
            return 
        }
        
        // 패킷 분석을 위한 로깅 (디버그 모드에서만)
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            val packetHex = lanternPacket.joinToString(" ") { String.format("%02X", it) }
            Log.d(TAG, "생성된 패킷(${lanternPacket.size}바이트): $packetHex")
        }

        // 광고 데이터 생성
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false) // 기기 이름 제외 (패킷 크기 최소화)
            .setIncludeTxPowerLevel(true) // TX 파워 레벨 포함 (거리 추정에 도움)
            .addManufacturerData(BleConstants.LANTERN_MANUFACTURER_ID_MESSAGE, lanternPacket)
            .build()

        // 디버깅을 위한 스캔 응답 데이터 추가 (이 부분이 스캔 시 추가 데이터로 제공됨)
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true) // 스캔 응답에는 기기 이름 포함
            .build()

        // 재시도 취소
        cancelPendingRetry()

        currentAdvertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                isCurrentlyAdvertising = true
                Log.i(TAG, "광고 성공: sID=$serverUserId, Nick='$processedNickname', MyDepth=$currentOwnAdvertisedDepth")
            }
            
            override fun onStartFailure(errorCode: Int) {
                isCurrentlyAdvertising = false
                // 에러 코드에 대한 명확한 메시지 출력
                val errorMsg = when (errorCode) {
                    ADVERTISE_FAILED_ALREADY_STARTED -> "이미 광고 중"
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "광고 데이터가 너무 큼"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "기기에서 BLE 광고를 지원하지 않음"
                    ADVERTISE_FAILED_INTERNAL_ERROR -> "내부 오류"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "광고자 수 제한 초과"
                    else -> "알 수 없는 오류($errorCode)"
                }
                Log.e(TAG, "광고 실패: $errorMsg. DataSize: ${lanternPacket.size}")
                
                // 내부 오류나 광고자 수 제한 초과일 경우에만 잠시 후 재시도
                if (errorCode == ADVERTISE_FAILED_INTERNAL_ERROR || 
                    errorCode == ADVERTISE_FAILED_TOO_MANY_ADVERTISERS) {
                    
                    val retryDelay = 3000L
                    Log.d(TAG, "${retryDelay/1000}초 후 광고 재시도...")
                    
                    pendingRetryRunnable = Runnable {
                        if (!isCurrentlyAdvertising) {
                            startAdvertising(serverUserId, processedNickname, currentOwnAdvertisedDepth)
                        }
                    }.also {
                        handler.postDelayed(it, retryDelay)
                    }
                }
            }
        }

        try {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "광고 시작 시도: sID=$serverUserId, Nick='$processedNickname', MyDepth=$currentOwnAdvertisedDepth")
                
                // 스캔 응답 데이터도 함께 설정
                advertiser.startAdvertising(settings, advertiseData, scanResponse, currentAdvertiseCallback)
            } else {
                Log.e(TAG, "BLUETOOTH_ADVERTISE 권한 없음")
                currentAdvertiseCallback?.onStartFailure(AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR)
            }
        } catch (e: Exception) {
            Log.e(TAG, "광고 시작 중 예외: ${e.message}", e)
            currentAdvertiseCallback?.onStartFailure(AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR)
        }
    }

    private fun cancelPendingRetry() {
        pendingRetryRunnable?.let { handler.removeCallbacks(it) }
        pendingRetryRunnable = null
    }

    private fun stopAdvertisingInternal(advertiser: BluetoothLeAdvertiser) {
        val activity = currentActivityRef?.get()
        if (activity != null) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED) {
                try {
                    currentAdvertiseCallback?.let { advertiser.stopAdvertising(it) }
                } catch (e: IllegalStateException) { /* 이미 중지된 경우 등 */ }
            }
        }
        isCurrentlyAdvertising = false
        currentAdvertiseCallback = null
        
        // 재시도 취소
        cancelPendingRetry()
    }

    fun stopAdvertising() {
        bluetoothLeAdvertiser?.let { stopAdvertisingInternal(it) }
    }
    
    // 메모리 누수를 방지하기 위해 Activity 참조를 제거하는 메서드
    fun release() {
        stopAdvertising()
        currentActivityRef = null
        bluetoothLeAdvertiser = null
        currentAdvertiseCallback = null
        cancelPendingRetry()
    }

    private fun buildLanternPacket(serverUserId: Long, processedNickname: String, currentOwnAdvertisedDepth: Int): ByteArray? {
        try {
            val userIdInt = serverUserId.toInt() // Long -> Int (값 범위 고려, 필요시 서버 ID 정책 변경)
            val nicknameBytes = processedNickname.toByteArray(StandardCharsets.UTF_8) // 이미 길이 처리된 닉네임

            // 패킷 구성: (1)데이터타입 + (1)버전 + (4)서버ID + (1)Depth + (1)닉네임길이 + (n)닉네임
            val packetSize = 1 + 1 + BleConstants.SERVER_USER_ID_BYTES + BleConstants.DEPTH_BYTES + 1 + nicknameBytes.size
            if (packetSize > 31) { // BLE 페이로드 최대 크기 엄격하게 체크 (헤더 등 제외하면 더 작음)
                Log.e(TAG, "최종 패킷 크기($packetSize)가 BLE 광고 제한(31바이트)을 초과합니다.")
                return null // 너무 크면 null 반환
            }
            
            // 디버그 수준 로그는 조건부로 실행 (성능 최적화)
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "패킷 구성: " +
                      "타입=${BleConstants.DATA_TYPE_LANTERN_V1}, " +
                      "버전=${BleConstants.PROTOCOL_VERSION_V1}, " +
                      "userID=$userIdInt, " +
                      "depth=$currentOwnAdvertisedDepth, " +
                      "nameLen=${nicknameBytes.size}, " +
                      "name='$processedNickname'")
            }
                  
            val buffer = ByteBuffer.allocate(packetSize).order(ByteOrder.LITTLE_ENDIAN)

            buffer.put(BleConstants.DATA_TYPE_LANTERN_V1)
            buffer.put(BleConstants.PROTOCOL_VERSION_V1)
            buffer.putInt(userIdInt)
            buffer.put(currentOwnAdvertisedDepth.toByte()) // 0-255 범위
            buffer.put(nicknameBytes.size.toByte())
            buffer.put(nicknameBytes)

            return buffer.array()
        } catch (e: Exception) {
            Log.e(TAG, "패킷 생성 오류: ${e.message}", e)
            return null
        }
    }
}