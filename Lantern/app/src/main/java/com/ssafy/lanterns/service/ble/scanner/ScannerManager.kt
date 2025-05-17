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

    fun updateChatSet(uuid: String, chat: String, activity: Activity){
        this.chatSet.add(uuid)
        saveChatSet(activity)
    }

    // 객체가 만들어지자 마자 실행 됨
    fun init(activity: Activity) {
        val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        loadChatSet(activity)
        Log.d("생성되나요?", "생성")
    }

    fun startScanning(activity: Activity, onMessageReceived: (String, String) -> Unit){
        if (bluetoothLeScanner == null) {
            Log.e("BLE", "BluetoothLeScanner is null")
            return
        }

        // 랜턴 앱 고유 UUID - 이 앱을 설치한 사용자 간 통신을 위한 식별자
        val LANTERN_APP_UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab")

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

                    val combined = menufacturerData?.let{
                        String(it)
                    }
                    val email = emailData?.let{
                        String(it)
                    }
                    Log.d("ScannerTest", "${combined}")

                    val emailText = email ?: "Unknown"

                    combined?.let{
                        val adParts = it.split("|", limit=2)
                        val scParts = email?.split("|", limit=2)
                        
                        // 랜턴 앱의 메시지 형식 검증 (UUID|메시지 형식인지 확인)
                        if(adParts.size == 2){
                            val uuid = adParts[0]
                            val admessage = adParts[1]
                            val email = scParts?.getOrNull(0)
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
                            Log.d("ScannerManager", "메시지 수신: 발신자=$email, 내용=$fullMessage")
                            onMessageReceived(email?:"Unknown", fullMessage)
                            
                            // 릴레이 코드 - 내가 받은 메시지를 다른 사용자에게 전달
                            val safeCombined = combined ?: ""
                            val safeEmail = emailText ?: ""
                            val dataList = listOf(safeCombined, safeEmail)
                            AdvertiserManager.startAdvertising(dataList, safeEmail, activity, 1)
                        }
                    }

                    Log.d("주소", "${scanResult.device.address}")
                }
                Log.d("스캔성공", "스캔 성공")
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e("스캔실패", "스캔 실패: $errorCode")
            }
        }

        try {
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
        } catch (e: SecurityException){
            Log.e("권한문제", "하기싷다 ")
        }

        // ✅ 여기에 1분마다 반복적으로 재시작 루프를 등록합니다.
        restartHandler.postDelayed(object : Runnable {
            override fun run() {
                Log.d("주기적으로", "🔄 주기적 스캔 재시작")
                stopScanning()
                startScanning(activity, onMessageReceived) // 재귀처럼 재시작
            }
        }, 1 * 60 * 1000) // 1분마다
    }

    fun stopScanning(){

//        scanCallback?.let{
//            try{
//                bluetoothLeScanner?.stopScan(scanCallback)
//            } catch (e : SecurityException){
//                Log.e("권한문제염", "하기싷다")
//            }
//        }
//
//        scanCallback = null
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: SecurityException){
            Log.e("권한문제", "하기싷다 ")
        }

        restartHandler.removeCallbacksAndMessages(null)
    }

    // UUID 유효성 검사 함수
    private fun isValidUUID(uuid: String): Boolean {
        // 간단한 UUID 형식 검사 (8자리 16진수)
        return uuid.length == 8 && uuid.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
    }
}