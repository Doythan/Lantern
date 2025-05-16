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

        // 랜턴 UUID
        val SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab")

//        val scanFilter = ScanFilter.Builder()
//            .setServiceUuid(ParcelUuid(SERVICE_UUID)) // 특정 UUID만 필터링
//            .build()
//
//        val scanFilters = listOf(scanFilter)

        val scanFilters = emptyList<ScanFilter>()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // 빠른 반응 모드
            .build()


        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                result?.let { scanResult ->
                    val menufacturerData = scanResult.scanRecord?.getManufacturerSpecificData(0xFFFF)
                    val emailData = scanResult.scanRecord?.getManufacturerSpecificData(0xFFFE)

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
                        if(adParts?.getOrNull(0) == "dbd26aba" || adParts?.getOrNull(0) == "78cd2d91" || adParts?.getOrNull(0) == "a16f6ca2" || adParts?.getOrNull(0) == "e145b0f0" || adParts?.getOrNull(0) == "d69739aa" || adParts?.getOrNull(0) == "dbd26aba" || adParts?.getOrNull(0) == "83000176" || adParts?.getOrNull(0) == "78cd2d91"){
                            chatSet.add(adParts?.getOrNull(0)?:"unknown")
                            saveChatSet(activity)
                        }
                        if(adParts.size == 2){
                            val uuid = adParts[0]
                            val admessage = adParts[1]
                            val email = scParts?.getOrNull(0)
                            val scmessage = scParts?.getOrNull(1)

                            // 이미 받은 uuid면
                            if(chatSet.contains(uuid)){
                                Log.d("중복", "이미받았습니다.")
                                return
                            }
                            val fullMessage = admessage + scmessage

                            chatSet.add(uuid)
                            saveChatSet(activity)

                            onMessageReceived(email?:"UnKnown", fullMessage)

                            Log.d("onScanResult", "${fullMessage}")
                        }

                        val safeCombined = combined ?: ""
                        val safeEmail = emailText ?: ""

                        val dataList = listOf(safeCombined, safeEmail)


                        // 릴레이 코드
                        AdvertiserManager.startAdvertising(dataList,emailText ,activity, 1)
                    }

//                    restartHandler.postDelayed({
//                        stopScanning()
//                        startScanning(activity, onMessageReceived)
//                    }, 1 * 60 * 1000)


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
}