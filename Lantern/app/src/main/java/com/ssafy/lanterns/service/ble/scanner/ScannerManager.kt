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

object ScannerManager {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private val chatSet = mutableSetOf<String>() // <uuid, chat>

    private val restartHandler = Handler(Looper.getMainLooper())
    private const val PREF_NAME = "ble_prefs"
    private const val KEY_CHAT_SET = "chat_uuids"

    private fun saveChatSet(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_CHAT_SET, chatSet).apply()
    }

    private fun loadChatSet(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet(KEY_CHAT_SET, null)
        if (savedSet != null) {
            chatSet.clear()
            chatSet.addAll(savedSet)
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
        Log.d("생성되나요?", "생성. BT Adapter: ${bluetoothAdapter != null}, Scanner: ${bluetoothLeScanner != null}")
    }

    fun startScanning(activity: Activity, onMessageReceived: (String, String) -> Unit){
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

        Log.d("스캔시작하냐?","채팅 스캔 시작 요청")

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
                    Log.d("ScannerTest", "Combined: ${combined}, EmailData: ${outerEmail}")

                    val emailText = outerEmail ?: "Unknown"

                    combined.let{
                        val adParts = it.split("|", limit=2)
                        val scParts = outerEmail?.split("|", limit=2)
                        
                        // 랜턴 앱의 메시지 형식 검증 (UUID|메시지 형식인지 확인)
                        if(adParts.size == 2){
                            val uuid = adParts[0]
                            val admessage = adParts[1]
                            val scannedSenderEmail = scParts?.getOrNull(0)
                            val scmessage = scParts?.getOrNull(1)

                            // 검증: UUID가 올바른 형식인지 확인 (UUID 형식 검증)
                            if (!isValidUUID(uuid)) {
                                Log.d("UUID검증", "유효하지 않은 UUID 형식: $uuid")
                                return
                            }

                            // 이미 받은 uuid면
                            if(chatSet.contains(uuid)){
                                Log.d("중복", "이미 수신한 메시지: $uuid")
                                return
                            }
                            
                            val fullMessage = if (scmessage != null) {
                                admessage + scmessage
                            } else {
                                admessage
                            }

                            // UUID를 기록하여 중복 메시지 수신 방지
                            chatSet.add(uuid)
                            saveChatSet(activity)

                            // 메시지 수신 콜백 호출
                            Log.d("ScannerManager", "메시지 수신: 발신자=${scannedSenderEmail?:emailText}, 내용=$fullMessage")
                            onMessageReceived(scannedSenderEmail ?: emailText, fullMessage)
                            
                            // 릴레이 코드 - 내가 받은 메시지를 다른 사용자에게 전달
                            val safeCombined = combined
                            val safeEmailForRelay = scannedSenderEmail ?: emailText
                            val dataList = listOf(safeCombined, safeEmailForRelay)
                            AdvertiserManager.startAdvertising(dataList, safeEmailForRelay, activity, 1)
                        }
                    }

                    Log.d("주소", "${scanResult.device.address}")
                }
                Log.d("스캔성공", "스캔 성공")
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e("스캔실패", "스캔 실패: $errorCode")
                // 스캔 실패 시에도 1분 후 재시작 로직은 유지할 수 있음 (선택 사항)
                // scheduleRestart(activity, onMessageReceived)
            }
        }

        try {
            // 권한 확인은 Activity 레벨에서 수행되었다고 가정합니다.
            // 실제로는 여기서도 ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) 확인하는 것이 안전합니다.
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
            Log.i("ScannerManager", "채팅 스캔 시작됨.")
        } catch (e: SecurityException){
            Log.e("권한문제", "채팅 스캔 시작 중 SecurityException: ${e.message}")
        } catch (e: IllegalStateException) {
            Log.e("BLE", "채팅 스캔 시작 중 IllegalStateException (Adapter off?): ${e.message}")
        }

        // ✅ 여기에 1분마다 반복적으로 재시작 루프를 등록합니다.
        scheduleRestart(activity, onMessageReceived)
    }

    private fun scheduleRestart(activity: Activity, onMessageReceived: (String, String) -> Unit) {
        restartHandler.postDelayed(object : Runnable {
            override fun run() {
                Log.d("주기적으로", "🔄 채팅 스캔 주기적 재시작")
                startScanning(activity, onMessageReceived)
            }
        }, 1 * 60 * 1000) // 1분마다
    }

    fun stopScanning(activity: Activity){ // Activity context 추가
        Log.i("ScannerManager", "채팅 스캔 중지 요청")
        if (bluetoothLeScanner == null) {
            Log.w("ScannerManager", "BluetoothLeScanner is null, cannot stop scan.")
            return
        }
        scanCallback?.let{ cb ->
            try{
                // BLUETOOTH_SCAN 권한 확인 (Android 12+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                        bluetoothLeScanner?.stopScan(cb)
                        Log.i("ScannerManager", "채팅 스캔 중지됨 (Android 12+).")
                    } else {
                        Log.e("ScannerManager", "BLUETOOTH_SCAN permission not granted for stopping scan on Android 12+.")
                    }
                } else {
                    // Android 11 이하에서는 BLUETOOTH_ADMIN 권한 필요 (매니페스트에 이미 선언되어 있다고 가정)
                    // 또는 별도 권한 없이도 stopScan 가능할 수 있음
                    bluetoothLeScanner?.stopScan(cb)
                    Log.i("ScannerManager", "채팅 스캔 중지됨 (Android 11 이하).")
                }
            } catch (e : SecurityException){
                Log.e("권한문제염", "채팅 스캔 중지 중 SecurityException: ${e.message}")
            } catch (e: IllegalStateException) {
                Log.e("BLE", "채팅 스캔 중지 중 IllegalStateException (Adapter off?): ${e.message}")
            }
        }
        scanCallback = null // 콜백 참조 제거
        restartHandler.removeCallbacksAndMessages(null) // 예약된 재시작 작업도 모두 제거
        Log.i("ScannerManager", "채팅 스캔 중지 완료 및 재시작 핸들러 제거됨.")
    }

    // UUID 유효성 검사 함수
    private fun isValidUUID(uuid: String): Boolean {
        // 간단한 UUID 형식 검사 (8자리 16진수)
        return uuid.length == 8 && uuid.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
    }
}