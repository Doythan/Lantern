package com.ssafy.lanterns.service.ble.scanner

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ssafy.lanterns.R
import com.ssafy.lanterns.service.ble.advertiser.AdvertiserManager
import java.util.UUID
import java.util.LinkedHashSet

object ScannerManager {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private val chatSet = LinkedHashSet<String>(500) // <uuid> - LinkedHashSet으로 변경하여 삽입 순서 유지, 용량 제한

    private val restartHandler = Handler(Looper.getMainLooper())
    private val scanCycleHandler = Handler(Looper.getMainLooper())
    private var isContinuousScanActive = false
    private var activityRefForScan: Activity? = null
    private var messageCallbackForScan: ((sender: String, text: String, isRelayed: Boolean) -> Unit)? = null

    // 스캔 주기 상수 - 최적화된 값으로 다시 조정
    private const val SCAN_HIGH_DUTY_DURATION_MS = 5000L  // 스캔 지속 시간 3초 (기존 3.5초)
    private const val SCAN_HIGH_DUTY_PAUSE_MS = 100L      // 스캔 간 휴식 시간 0.1초 (기존 0.1초)
    
    // 채팅 세트 관리 상수
    private const val MAX_CHAT_SET_SIZE = 500 // 최대 UUID 저장 개수
    private const val PREF_NAME = "ble_prefs"
    private const val KEY_CHAT_SET = "chat_uuids"

    private fun saveChatSet(context: Context) {
        // 채팅 세트 크기 제한 처리
        if (chatSet.size > MAX_CHAT_SET_SIZE) {
            val overflow = chatSet.size - MAX_CHAT_SET_SIZE
            // LinkedHashSet은 삽입 순서를 보존하므로 가장 오래된 것부터 제거
            val iterator = chatSet.iterator()
            var count = 0
            while (iterator.hasNext() && count < overflow) {
                iterator.next()
                iterator.remove()
                count++
            }
        }
        
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_CHAT_SET, chatSet).apply()
    }

    private fun loadChatSet(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet(KEY_CHAT_SET, null)
        if (savedSet != null) {
            chatSet.clear()
            chatSet.addAll(savedSet)
            Log.d("ScannerManager", "채팅 세트 로드: ${chatSet.size}개 항목")
        }
    }

    fun updateChatSet(uuid: String, activity: Activity){
        this.chatSet.add(uuid)
        saveChatSet(activity)
    }

    // 객체가 만들어지자 마자 실행 됨
    fun init(activity: Activity) {
        val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        loadChatSet(activity)
        Log.d("ScannerManager", "스캐너 초기화: BT Adapter: ${bluetoothAdapter != null}, Scanner: ${bluetoothLeScanner != null}")
    }

    fun startScanning(activity: Activity, onMessageReceived: (sender: String, text: String, isRelayed: Boolean) -> Unit){
        // 스캔 중이면 이전 스캔 중지
        stopScanning(activity)
        
        this.activityRefForScan = activity
        this.messageCallbackForScan = onMessageReceived
        
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.e("BLE", "Bluetooth Adapter is null or not enabled. Cannot start scan.")
            return
        }
        if (bluetoothLeScanner == null) {
            Log.e("BLE", "BluetoothLeScanner is null. Trying to re-initialize.")
            init(activity) // 스캐너가 null이면 초기화 시도
            if (bluetoothLeScanner == null) {
                Log.e("BLE", "BluetoothLeScanner is still null after re-init. Cannot start scan.")
                return
            }
        }

        // 연속 스캔 시작
        isContinuousScanActive = true
        startScanInternalLogic()
    }

    // 내부 스캔 시작 로직 (주기적으로 호출됨)
    private fun startScanInternalLogic() {
        if (!isContinuousScanActive || activityRefForScan == null || messageCallbackForScan == null) {
            Log.d("ScannerManager", "스캔 중단 상태: isContinuousScanActive=$isContinuousScanActive")
            return
        }
        
        val activity = activityRefForScan ?: return
        val onMessageReceived = messageCallbackForScan ?: return

        Log.d("ScannerManager","채팅 스캔 시작 (주기: ${SCAN_HIGH_DUTY_DURATION_MS}ms 스캔, ${SCAN_HIGH_DUTY_PAUSE_MS}ms 휴식)")

        // 제조사 ID - 랜턴 앱 전용 식별자 (0xFFFF, 0xFFFE 사용)
        val LANTERN_MANUFACTURER_ID_MESSAGE = 0xFFFF
        val LANTERN_MANUFACTURER_ID_EMAIL = 0xFFFE

        // ScanFilter를 사용하여 특정 제조사 ID만 필터링
        val scanFilter1 = ScanFilter.Builder()
            .setManufacturerData(LANTERN_MANUFACTURER_ID_MESSAGE, null)
            .build()
            
        val scanFilter2 = ScanFilter.Builder()
            .setManufacturerData(LANTERN_MANUFACTURER_ID_EMAIL, null)
            .build()
            
        val scanFilters = listOf(scanFilter1, scanFilter2)

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // 빠른 반응 모드
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) // 모든 일치에 대해 콜백 받기
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE) // 적극적인 탐지 모드
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT) // 최대 광고 매칭 횟수
            .setReportDelay(0L) // 지연 없이 즉시 보고
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                result?.let { scanResult ->
                    val menufacturerData = scanResult.scanRecord?.getManufacturerSpecificData(LANTERN_MANUFACTURER_ID_MESSAGE)
                    val emailData = scanResult.scanRecord?.getManufacturerSpecificData(LANTERN_MANUFACTURER_ID_EMAIL)

                    if(menufacturerData == null) return

                    val combined = menufacturerData.let{
                        String(it)
                    }
                    val outerEmail = emailData?.let{
                        String(it)
                    }
                    // Log.d("ScannerTest", "Combined: ${combined}, EmailData: ${outerEmail}")

                    val emailText = outerEmail ?: "Unknown"

                    combined.let{
                        // 새로운 TTL 형식 파싱 (UUID|TTL|메시지)
                        val adParts = it.split("|", limit=3)
                        val scParts = outerEmail?.split("|", limit=2)
                        
                        // 새 형식 (UUID|TTL|메시지) 또는 구 형식 (UUID|메시지) 구분
                        if(adParts.size >= 2){
                            val uuid = adParts[0]
                            
                            // 검증: UUID가 올바른 형식인지 확인 (UUID 형식 검증)
                            if (!isValidUUID(uuid)) {
                                Log.d("UUID검증", "유효하지 않은 UUID 형식: $uuid (길이: ${uuid.length})")
                                return
                            }

                            // 이미 받은 uuid면
                            if(chatSet.contains(uuid)){
                                // Log.d("중복", "이미 수신한 메시지: $uuid")
                                return
                            }
                            
                            // TTL과 메시지 추출
                            var ttl = 0
                            var admessage = ""
                            var isRelayed = false
                            
                            if (adParts.size >= 3) {
                                // 새 TTL 형식 (UUID|TTL|메시지)
                                try {
                                    ttl = adParts[1].toInt()
                                } catch (e: NumberFormatException) {
                                    // TTL 파싱 실패시 기본값 0 (더 이상 릴레이 안 함)
                                    Log.d("TTL검증", "유효하지 않은 TTL 값: ${adParts[1]}")
                                }
                                admessage = adParts[2]
                                isRelayed = ttl < 5 // 최초 발신 TTL이 5일 때, 5보다 작으면 릴레이된 메시지
                            } else {
                                // 구 형식 (UUID|메시지) - 하위 호환성 유지
                                admessage = adParts[1]
                                // 구 형식에서는 [R] 접두사로 릴레이 여부 파악
                                if (admessage.startsWith("[R] ")) {
                                    admessage = admessage.substring(4) // "[R] " 제거
                                    isRelayed = true
                                    ttl = 0 // 이미 한 번 릴레이됨, 더 이상 릴레이하지 않음
                                } else {
                                    ttl = 1 // 아직 릴레이되지 않음, 한 번만 릴레이
                                }
                            }
                            
                            val scannedSenderEmail = scParts?.getOrNull(0)
                            val scmessage = scParts?.getOrNull(1)
                            
                            val fullMessage = if (scmessage != null) {
                                admessage + scmessage
                            } else {
                                admessage
                            }

                            // UUID를 기록하여 중복 메시지 수신 방지
                            chatSet.add(uuid)
                            saveChatSet(activity)

                            // 메시지 수신 콜백 호출
                            Log.d("ScannerManager", "메시지 수신: 발신자=${scannedSenderEmail?:emailText}, 내용='$fullMessage', 릴레이=$isRelayed, TTL=$ttl")
                            onMessageReceived(scannedSenderEmail ?: emailText, fullMessage, isRelayed)
                            
                            // TTL > 0일 때만 릴레이
                            if (ttl > 0) {
                                // 릴레이 코드 - 내가 받은 메시지를 다른 사용자에게 전달
                                Log.d("ScannerManager", "메시지 릴레이: TTL=$ttl, UUID=$uuid")
                                val relayDataList = listOf(combined, outerEmail ?: "Unknown") // 원본 combined 사용
                                AdvertiserManager.startAdvertising(relayDataList, scannedSenderEmail ?: emailText, activity, 1)
                            } else {
                                Log.d("ScannerManager", "TTL=0으로 릴레이하지 않음: UUID=$uuid")
                            }
                        }
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e("ScannerManager", "스캔 실패: $errorCode")
            }
        }

        try {
            // 권한 확인
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    Log.e("ScannerManager", "BLUETOOTH_SCAN 권한이 없습니다. 스캔을 시작할 수 없습니다.")
                    return
                }
            }
            
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
            
            
            // 지정된 시간 후에 스캔 일시 중지
            scanCycleHandler.postDelayed({
                stopCurrentScanSession()
                
                // 짧은 휴식 후 다시 스캔 시작
                scanCycleHandler.postDelayed({
                    if (isContinuousScanActive) {
                        startScanInternalLogic() // 스캔 다시 시작
                    }
                }, SCAN_HIGH_DUTY_PAUSE_MS)
            }, SCAN_HIGH_DUTY_DURATION_MS)
            
        } catch (e: SecurityException){
            Log.e("ScannerManager", "채팅 스캔 시작 중 SecurityException: ${e.message}")
        } catch (e: IllegalStateException) {
            Log.e("ScannerManager", "채팅 스캔 시작 중 IllegalStateException (Adapter off?): ${e.message}")
        }
    }
    
    // 현재 진행 중인 스캔 세션만 중지 (전체 스캔 사이클은 계속)
    private fun stopCurrentScanSession() {
        val activity = activityRefForScan ?: return
        
        scanCallback?.let{ cb ->
            try{
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                        bluetoothLeScanner?.stopScan(cb)
                        Log.i("ScannerManager", "채팅 스캔 일시 중지됨.")
                    } else {
                        Log.e("ScannerManager", "BLUETOOTH_SCAN 권한이 없습니다. 스캔을 중지할 수 없습니다.")
                    }
                } else {
                    bluetoothLeScanner?.stopScan(cb)
                    Log.i("ScannerManager", "채팅 스캔 일시 중지됨.")
                }
            } catch (e : SecurityException){
                Log.e("ScannerManager", "채팅 스캔 중지 중 SecurityException: ${e.message}")
            } catch (e: IllegalStateException) {
                Log.e("ScannerManager", "채팅 스캔 중지 중 IllegalStateException (Adapter off?): ${e.message}")
            }
        }
        scanCallback = null
    }

    fun stopScanning(activity: Activity){
        Log.i("ScannerManager", "채팅 스캔 중지 요청")
        
        // 스캔 루프 중지
        isContinuousScanActive = false
        scanCycleHandler.removeCallbacksAndMessages(null)
        restartHandler.removeCallbacksAndMessages(null)
        
        // 현재 진행 중인 스캔 세션 중지
        stopCurrentScanSession()
        
        // 콜백 참조 정리
        activityRefForScan = null
        messageCallbackForScan = null
        
        Log.i("ScannerManager", "채팅 스캔 중지 완료 및 핸들러 제거됨.")
    }

    // UUID 유효성 검사 함수
    private fun isValidUUID(uuid: String): Boolean {
        // UUID 형식 검사를 6자리로 변경
        return uuid.length == 6 && uuid.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
    }
}