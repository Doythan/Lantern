package com.ssafy.lanterns.service.ble.advertiser

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID
import com.ssafy.lanterns.service.ble.scanner.ScannerManager

object AdvertiserManager{
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback : AdvertiseCallback? = null

    private val restartHandler = Handler(Looper.getMainLooper()) // main thread에서 일정시간 뒤에 호출하는 callback 함수
    private var lastMessage: String = ""
    private var lastState: Int = 0
    
    // 광고 모드 상수
    const val ADVERTISING_MODE_CONTINUOUS = 0 // 30초 연속 광고
    const val ADVERTISING_MODE_SPLIT = 1 // 5초 간격 3회 분할 광고
    
    // 광고 시간 관련 상수
    private const val CONTINUOUS_ADVERTISING_TIME_MS = 30 * 1000 // 30초
    private const val SPLIT_ADVERTISING_TIME_MS = 10 * 1000 // 10초
    private const val SPLIT_ADVERTISING_INTERVAL_MS = 3 * 1000 // 3초
    private const val SPLIT_ADVERTISING_CLEANUP_MS = 13 * 1000 // 13초
    private const val QUEUE_PROCESS_INTERVAL_MS = 10 * 1000 // 10초
    
    // 현재 광고 모드 (기본값: 연속 모드)
    private var currentMode = ADVERTISING_MODE_CONTINUOUS
    
    // 분할 광고 모드를 위한 현재 광고 정보 저장
    private var currentAdvertisingData: List<String>? = null
    private var currentEmail: String? = null
    private var currentActivity: Activity? = null
    private var currentState: Int = 0
    private var currentSplitUUID: String? = null // 분할 모드에서 사용하는 UUID 저장

    // 랜턴 앱 전용 상수
    private const val LANTERN_MANUFACTURER_ID_MESSAGE = 0xFFFF
    private const val LANTERN_MANUFACTURER_ID_EMAIL = 0xFFFE
    private val LANTERN_APP_UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab")
    
    // BLE 데이터 제한 관련 상수
    private const val MAX_BLE_DATA_SIZE = 20 // BLE 메시지 최대 사이즈 20바이트
    
    // 최대 TTL 값 설정 (이 값을 초과하면 릴레이하지 않음)
    private const val MAX_TTL = 5 // 최대 5회 릴레이
    
    // 메시지 큐 관련 상수 및 변수
    private const val MAX_ACTIVE_ADVERTISEMENTS = 5 // 최대 동시 실행 광고 수
    private const val MINIMUM_ADVERTISING_TIME = 10000L // 최소 광고 시간(ms)
    private val messageQueue = mutableListOf<QueuedMessage>() // 메시지 큐
    private var isProcessingQueue = false // 큐 처리 중 여부
    
    // 활성 광고 목록
    private val activeAdvertisements = mutableListOf<ActiveAdvertisement>()
    
    // 큐에 저장할 메시지 데이터 클래스
    data class QueuedMessage(
        val messageData: List<String>,
        val email: String,
        val activity: Activity,
        val state: Int
    )
    
    // 활성 광고 정보 데이터 클래스
    data class ActiveAdvertisement(
        val callback: AdvertiseCallback,
        val messageData: List<String>,
        val email: String,
        val activity: Activity,
        val state: Int,
        val expiryTime: Long,
        val startTime: Long = System.currentTimeMillis()
    )

    fun init(activity: Activity) {
        val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
    }
    
    // 광고 모드 설정 함수
    fun setAdvertisingMode(mode: Int) {
        currentMode = mode
        Log.d("AdvertiserManager", "광고 모드 변경: ${if(mode == ADVERTISING_MODE_CONTINUOUS) "연속 모드" else "분할 모드"}")
    }

    // 메시지 데이터 준비 (크기 초과 시 로그만 출력)
    private fun prepareMessageData(uuid: String, ttl: Int, message: String): ByteArray {
        val combined = "$uuid|$ttl|$message"
        val bytes = combined.toByteArray()
        
        // 메시지가 최대 크기를 초과하는 경우 로그만 출력
        if (bytes.size > MAX_BLE_DATA_SIZE) {
            Log.e("AdvertiserManager", "광고 데이터 크기 초과! ${bytes.size}바이트")
        }
        
        return bytes
    }
    
    // 광고 설정 생성 (공통)
    private fun createAdvertiseSettings(): AdvertiseSettings {
        return AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0) // 타임아웃 없이 계속 광고
            .build()
    }

    // 광고 시작 함수 (개선됨)
    fun startAdvertising(messageList: List<String>, email: String, activity: Activity, state: Int) {
        cleanupExpiredAdvertisements()
        
        // 현재 활성 광고가 최대치이면
        if (activeAdvertisements.size >= MAX_ACTIVE_ADVERTISEMENTS) {
            // 충분히 광고된 메시지만 선택적으로 제거
            val now = System.currentTimeMillis()
            val oldEnoughAds = activeAdvertisements.filter { 
                now - it.startTime >= MINIMUM_ADVERTISING_TIME 
            }
            
            if (oldEnoughAds.isNotEmpty()) {
                // 충분히 광고된 메시지 중 가장 오래된 것 제거
                val oldest = oldEnoughAds.minByOrNull { it.expiryTime }
                oldest?.let { removeAdvertisement(it.callback) }
            } else {
                // 충분히 광고된 메시지가 없으면 광고 큐에 추가
                Log.d("AdvertiserManager", "모든 광고가 최소 시간에 도달하지 않음 - 큐에 추가")
                queueMessage(messageList, email, activity, state)
                return
            }
        }
        
        // 새 광고 시작
        startRealAdvertising(messageList, email, activity, state)
    }
    
    // 광고 실행 함수 - 모드에 따라 적절한 광고 방식 선택
    private fun startRealAdvertising(messageList: List<String>, email: String, activity: Activity, state: Int) {
        // 현재 설정된 모드에 따라 적절한 광고 함수 호출
        if (currentMode == ADVERTISING_MODE_CONTINUOUS) {
            startRealContinuousAdvertising(messageList, email, activity, state)
        } else {
            startRealSplitAdvertising(messageList, email, activity, state)
        }
    }
    
    // 메시지 큐에 추가
    private fun queueMessage(messageList: List<String>, email: String, activity: Activity, state: Int) {
        messageQueue.add(QueuedMessage(messageList, email, activity, state))
        Log.d("AdvertiserManager", "메시지 큐에 추가됨 (큐 크기: ${messageQueue.size})")
        
        // 큐 처리가 아직 진행 중이 아니면 시작
        if (!isProcessingQueue) {
            startProcessingQueue()
        }
    }
    
    // 큐 처리 시작
    private fun startProcessingQueue() {
        if (messageQueue.isEmpty() || isProcessingQueue) {
            return
        }
        
        isProcessingQueue = true
        
        // 큐 처리 주기로 다음 메시지 처리 시도
        restartHandler.postDelayed({
            if (messageQueue.isNotEmpty()) {
                // 큐에서 첫 번째 메시지 추출
                val nextMessage = messageQueue.removeAt(0)
                
                // 광고 수 확인 및 시작
                cleanupExpiredAdvertisements()
                if (activeAdvertisements.size < MAX_ACTIVE_ADVERTISEMENTS) {
                    startRealAdvertising(
                        nextMessage.messageData,
                        nextMessage.email,
                        nextMessage.activity,
                        nextMessage.state
                    )
                } else {
                    // 아직 공간이 없으면 다시 큐에 추가
                    messageQueue.add(0, nextMessage)
                }
            }
            
            // 큐에 메시지가 남아있으면 계속 처리
            if (messageQueue.isNotEmpty()) {
                startProcessingQueue()
            } else {
                isProcessingQueue = false
            }
        }, QUEUE_PROCESS_INTERVAL_MS.toLong())
    }
    
    // 만료된 광고 정리
    private fun cleanupExpiredAdvertisements() {
        val now = System.currentTimeMillis()
        val expiredAds = activeAdvertisements.filter { it.expiryTime <= now }
        
        for (ad in expiredAds) {
            removeAdvertisement(ad.callback)
        }
    }
    
    // 광고 제거
    private fun removeAdvertisement(callback: AdvertiseCallback) {
        try {
            bluetoothLeAdvertiser?.stopAdvertising(callback)
            
            // 활성 광고 목록에서 제거
            activeAdvertisements.removeIf { it.callback == callback }
            Log.d("AdvertiserManager", "광고 제거됨 (남은 광고: ${activeAdvertisements.size}개)")
        } catch (e: SecurityException) {
            Log.e("AdvertiserManager", "광고 제거 실패: ${e.message}")
        } catch (e: IllegalStateException) {
            Log.e("AdvertiserManager", "광고 제거 실패 (블루투스 상태 오류): ${e.message}")
        }
    }
    
    // 실제 연속 광고 시작 (30초)
    private fun startRealContinuousAdvertising(messageList: List<String>, email: String, activity: Activity, state: Int) {
        // Advertise Setting
        val settings = createAdvertiseSettings()

        var advertiseData: AdvertiseData? = null
        var scanResponseData: AdvertiseData? = null

        // 최초로 데이터를 보낼 때
        if(state == 0) {
            // 메시지 고유 UUID 생성 (6자리)
            val uuid = UUID.randomUUID().toString().substring(0, 6)
            val adString = messageList.getOrNull(0) ?: ""
            val scString = messageList.getOrNull(1) ?: ""
            Log.d("AdvertiserManager", "메시지 전송: ${adString}, ${scString}")
            
            // TTL=MAX_TTL로 시작 (최초 발신)
            val adBytes = prepareMessageData(uuid, MAX_TTL, adString)
            val scCombined = "$email|$scString"
            val scBytes = scCombined.toByteArray()

            // 데이터 크기 확인 및 로깅
            Log.d("AdvertiserManager", "AdBytes Size: ${adBytes.size}, SRBytes Size: ${scBytes.size}")
            if (scBytes.size > MAX_BLE_DATA_SIZE) {
                Log.e("AdvertiserManager", "스캔 응답 데이터 크기 초과! SR: ${scBytes.size}")
            }

            // 내 기기에서도 메시지 수신시 중복 방지를 위한 처리
            ScannerManager.updateChatSet(uuid, activity)

            // 메시지 데이터 광고
            advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addManufacturerData(LANTERN_MANUFACTURER_ID_MESSAGE, adBytes)
                .build()

            // 이메일 및 추가 데이터 응답
            scanResponseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addManufacturerData(LANTERN_MANUFACTURER_ID_EMAIL, scBytes)
                .build()
        }
        // 메시지 릴레이인 경우
        else{
            val originalAdCombined = messageList.getOrNull(0) ?: ""
            val originalScCombined = messageList.getOrNull(1) ?: ""

            // TTL 처리 (UUID|TTL|메시지 형식)
            val adParts = originalAdCombined.split("|", limit = 3)
            
            if (adParts.size >= 3) {
                val uuid = adParts[0]
                val ttl = try {
                    adParts[1].toInt()
                } catch (e: NumberFormatException) {
                    1 // 변환 실패 시 기본값
                }
                val message = adParts[2]
                
                // TTL 감소
                val newTtl = ttl - 1
                
                if (newTtl <= 0) {
                    // TTL이 0 이하면 더 이상 릴레이하지 않음
                    Log.d("AdvertiserManager", "TTL 만료로 릴레이 중단: UUID=$uuid, TTL=$newTtl")
                    return
                }
                
                // 새 TTL로 광고 데이터 생성
                val adBytes = prepareMessageData(uuid, newTtl, message)
                val scBytes = originalScCombined.toByteArray()
                
                Log.d("AdvertiserManager", "릴레이 광고: TTL=$newTtl, UUID=$uuid")
                
                // 데이터 크기 확인 및 로깅
                Log.d("AdvertiserManager", "릴레이 AdBytes Size: ${adBytes.size}, SRBytes Size: ${scBytes.size}")
                if (scBytes.size > MAX_BLE_DATA_SIZE) {
                    Log.e("AdvertiserManager", "릴레이 스캔 응답 데이터 크기 초과! SR: ${scBytes.size}")
                }

                advertiseData = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addManufacturerData(LANTERN_MANUFACTURER_ID_MESSAGE, adBytes)
                    .build()

                scanResponseData = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addManufacturerData(LANTERN_MANUFACTURER_ID_EMAIL, scBytes)
                    .build()
            } else {
                // 구 버전 호환성을 위한 코드 (TTL이 없는 메시지를 받았을 경우)
                Log.d("AdvertiserManager", "구 형식 릴레이 메시지: $originalAdCombined")
                
                // 구형 메시지도 TTL 포맷으로 변환
                val uuid = if (adParts.size == 2) adParts[0] else "unknown"
                val message = if (adParts.size == 2) adParts[1] else originalAdCombined
                val adBytes = prepareMessageData(uuid, 1, message)
                
                advertiseData = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addManufacturerData(LANTERN_MANUFACTURER_ID_MESSAGE, adBytes)
                    .build()

                scanResponseData = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addManufacturerData(LANTERN_MANUFACTURER_ID_EMAIL, originalScCombined.toByteArray())
                    .build()
            }
        }

        // 광고 만료 시간 계산 (30초 후)
        val expiryTime = System.currentTimeMillis() + CONTINUOUS_ADVERTISING_TIME_MS

        // 광고 콜백
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                super.onStartSuccess(settingsInEffect)
                Log.d("AdvertiserManager", "광고 시작 성공")
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                Log.e("AdvertiserManager", "광고 시작 실패: 에러 코드 ${errorCode}")
            }
        }
        
        // 권한 확인 후 광고 시작
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED) {
            bluetoothLeAdvertiser?.startAdvertising(settings, advertiseData, scanResponseData, callback)
            
            // 활성 광고 목록에 추가
            val activeAd = ActiveAdvertisement(
                callback = callback,
                messageData = messageList,
                email = email,
                activity = activity,
                state = state,
                expiryTime = expiryTime
            )
            activeAdvertisements.add(activeAd)
            Log.d("AdvertiserManager", "새 광고 추가됨 (총 ${activeAdvertisements.size}개 활성)")
            
            // 30초 후 자동 정리를 위한 타이머 설정
            restartHandler.postDelayed({
                removeAdvertisement(callback)
            }, CONTINUOUS_ADVERTISING_TIME_MS.toLong())
            
        } else {
            Log.e("BLE", "BLUETOOTH_ADVERTISE 권한이 없습니다. Advertising을 시작할 수 없습니다.")
        }
    }
    
    // 새로운 분할 광고 방식 (10초 x 3회)
    private fun startRealSplitAdvertising(messageList: List<String>, email: String, activity: Activity, state: Int) {
        // 현재 파라미터 저장
        currentAdvertisingData = messageList
        currentEmail = email
        currentActivity = activity
        currentState = state
        
        // 새 메시지면 UUID 초기화 (첫 번째 분할 광고 시작할 때)
        if (state == 0) {
            currentSplitUUID = UUID.randomUUID().toString().substring(0, 6)
            Log.d("AdvertiserManager", "분할 광고용 새 UUID 생성: $currentSplitUUID")
        }
        
        // 첫 번째 광고 시작
        startRealSplitAdvertisingInternal()
        
        // 5초 간격으로 추가 광고 스케줄링
        restartHandler.postDelayed({
            if (currentAdvertisingData != null) {
                startRealSplitAdvertisingInternal()
            }
        }, SPLIT_ADVERTISING_INTERVAL_MS.toLong()) // 5초 후 두 번째 광고

        restartHandler.postDelayed({
            if (currentAdvertisingData != null) {
                startRealSplitAdvertisingInternal()
            }
        }, (SPLIT_ADVERTISING_INTERVAL_MS * 2).toLong()) // 10초 후 세 번째 광고
    }
    
    private fun startRealSplitAdvertisingInternal() {
        val messageList = currentAdvertisingData ?: return
        val email = currentEmail ?: return
        val activity = currentActivity ?: return
        val state = currentState
        
        // Advertise Setting
        val settings = createAdvertiseSettings()

        var advertiseData: AdvertiseData? = null
        var scanResponseData: AdvertiseData? = null

        // 최초로 데이터를 보낼 때
        if(state == 0) {
            // 분할 모드에서는 저장된 UUID 사용
            val uuid = currentSplitUUID ?: UUID.randomUUID().toString().substring(0, 6)
            val adString = messageList.getOrNull(0) ?: ""
            val scString = messageList.getOrNull(1) ?: ""
            Log.d("AdvertiserManager", "분할 광고 메시지 전송: UUID=$uuid, 메시지=${adString}, ${scString}")
            
            // TTL=MAX_TTL로 시작 (최초 발신)
            val adBytes = prepareMessageData(uuid, MAX_TTL, adString)
            val scCombined = "$email|$scString"
            val scBytes = scCombined.toByteArray()

            // 데이터 크기 확인 및 로깅
            Log.d("AdvertiserManager", "분할 광고 AdBytes Size: ${adBytes.size}, SRBytes Size: ${scBytes.size}")
            if (scBytes.size > MAX_BLE_DATA_SIZE) {
                Log.e("AdvertiserManager", "분할 광고 스캔 응답 데이터 크기 초과! SR: ${scBytes.size}")
            }

            // 내 기기에서도 메시지 수신시 중복 방지를 위한 처리
            ScannerManager.updateChatSet(uuid, activity)

            // 메시지 데이터 광고
            advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addManufacturerData(LANTERN_MANUFACTURER_ID_MESSAGE, adBytes)
                .build()

            // 이메일 및 추가 데이터 응답
            scanResponseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addManufacturerData(LANTERN_MANUFACTURER_ID_EMAIL, scBytes)
                .build()
        }
        // 메시지 릴레이인 경우
        else {
            val originalAdCombined = messageList.getOrNull(0) ?: ""
            val originalScCombined = messageList.getOrNull(1) ?: ""

            // TTL 처리 (UUID|TTL|메시지 형식)
            val adParts = originalAdCombined.split("|", limit = 3)
            
            if (adParts.size >= 3) {
                val uuid = adParts[0]
                val ttl = try {
                    adParts[1].toInt()
                } catch (e: NumberFormatException) {
                    1 // 변환 실패 시 기본값
                }
                val message = adParts[2]
                
                // TTL 감소
                val newTtl = ttl - 1
                
                if (newTtl <= 0) {
                    // TTL이 0 이하면 더 이상 릴레이하지 않음
                    Log.d("AdvertiserManager", "TTL 만료로 릴레이 중단: UUID=$uuid, TTL=$newTtl")
                    return
                }
                
                // 새 TTL로 광고 데이터 생성
                val adBytes = prepareMessageData(uuid, newTtl, message)
                val scBytes = originalScCombined.toByteArray()
                
                Log.d("AdvertiserManager", "분할 릴레이 광고: TTL=$newTtl, UUID=$uuid")
                
                // 데이터 크기 확인 및 로깅
                Log.d("AdvertiserManager", "분할 릴레이 AdBytes Size: ${adBytes.size}, SRBytes Size: ${scBytes.size}")
                if (scBytes.size > MAX_BLE_DATA_SIZE) {
                    Log.e("AdvertiserManager", "분할 릴레이 스캔 응답 데이터 크기 초과! SR: ${scBytes.size}")
                }

                advertiseData = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addManufacturerData(LANTERN_MANUFACTURER_ID_MESSAGE, adBytes)
                    .build()

                scanResponseData = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addManufacturerData(LANTERN_MANUFACTURER_ID_EMAIL, scBytes)
                    .build()
            } else {
                // 구 버전 호환성을 위한 코드 (TTL이 없는 메시지를 받았을 경우)
                Log.d("AdvertiserManager", "구 형식 분할 릴레이 메시지: $originalAdCombined")
                
                // 구형 메시지도 TTL 포맷으로 변환
                val uuid = if (adParts.size == 2) adParts[0] else "unknown"
                val message = if (adParts.size == 2) adParts[1] else originalAdCombined
                val adBytes = prepareMessageData(uuid, 1, message)
                
                advertiseData = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addManufacturerData(LANTERN_MANUFACTURER_ID_MESSAGE, adBytes)
                    .build()

                scanResponseData = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addManufacturerData(LANTERN_MANUFACTURER_ID_EMAIL, originalScCombined.toByteArray())
                    .build()
            }
        }

        // 광고 만료 시간 계산 (10초 후)
        val expiryTime = System.currentTimeMillis() + SPLIT_ADVERTISING_TIME_MS

        // 광고 콜백
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                super.onStartSuccess(settingsInEffect)
                Log.d("AdvertiserManager", "분할 광고 시작 성공")
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                Log.e("AdvertiserManager", "분할 광고 시작 실패: 에러 코드 ${errorCode}")
            }
        }
        
        // 권한 확인 후 광고 시작
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED) {
            bluetoothLeAdvertiser?.startAdvertising(settings, advertiseData, scanResponseData, callback)
            
            // 활성 광고 목록에 추가
            val activeAd = ActiveAdvertisement(
                callback = callback,
                messageData = messageList,
                email = email,
                activity = activity,
                state = state,
                expiryTime = expiryTime
            )
            activeAdvertisements.add(activeAd)
            Log.d("AdvertiserManager", "새 분할 광고 추가됨 (총 ${activeAdvertisements.size}개 활성)")
            
            // 광고 정리 타이머 설정
            restartHandler.postDelayed({
                removeAdvertisement(callback)
            }, SPLIT_ADVERTISING_CLEANUP_MS.toLong())
            
        } else {
            Log.e("BLE", "BLUETOOTH_ADVERTISE 권한이 없습니다. 분할 Advertising을 시작할 수 없습니다.")
        }
    }
    
    // 모든 광고 중지 및 타이머 제거
    fun stopAdvertising(){
        // 모든 활성 광고 중지
        val advertisementsToStop = ArrayList(activeAdvertisements)
        for (ad in advertisementsToStop) {
            removeAdvertisement(ad.callback)
        }
        
        // 타이머 제거
        restartHandler.removeCallbacksAndMessages(null)
        
        // 분할 광고 모드 데이터 정리
        currentAdvertisingData = null
        currentEmail = null
        currentActivity = null
        currentState = 0
        currentSplitUUID = null
        
        // 메시지 큐 초기화
        messageQueue.clear()
        isProcessingQueue = false
        
        Log.d("AdvertiserManager", "모든 광고 중지 및 타이머 제거")
    }
}