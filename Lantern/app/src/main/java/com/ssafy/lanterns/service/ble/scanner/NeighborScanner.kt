package com.ssafy.lanterns.service.ble.scanner

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

object NeighborScanner {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    val userMap = mutableMapOf<String, NearbyUser>() // <nickname, user object>

    private val handler = Handler(Looper.getMainLooper())
    private const val SCAN_TIMEOUT = 3000L // 3 seconds

    private val restartHandler = Handler(Looper.getMainLooper())
    private const val PREF_NAME = "ble_prefs"
    private const val KEY_CHAT_SET = "chat_uuids"

    data class NearbyUser(
        val nickname: String,
        val depth: Int,
        var lastSeen: Long,
        val lat: Int,
        val lng: Int
    )

//    private fun saveDataSet(context: Context) {
//        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
//        prefs.edit().putStringSet(KEY_CHAT_SET, dataSet).apply()
//    }
//
//    private fun loadDataSet(context: Context) {
//        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
//        val savedSet = prefs.getStringSet(KEY_CHAT_SET, null)
//        if (savedSet != null) {
//            dataSet.clear()
//            dataSet.addAll(savedSet)
//        }
//    }
//
//    fun updateChatSet(uuid: String, chat: String, activity: Activity){
//        this.dataSet.add(uuid)
//        saveDataSet(activity)
//    }

    fun init(activity: Activity) {
        val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
//        loadDataSet(activity)
        Log.d("생성되나요?", "생성")
    }

    fun startScanning(activity: Activity, onMessageReceived: (String, String) -> Unit){
        if (bluetoothLeScanner == null) {
            Log.e("BLE", "BluetoothLeScanner is null")
            return
        }

        val scanFilters = emptyList<ScanFilter>()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // 빠른 반응 모드
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                result?.let{
                    scanResult ->
                    val menufacturerData = scanResult.scanRecord?.getManufacturerSpecificData(0xFFFF)
                    val latLng = scanResult.scanRecord?.getManufacturerSpecificData(0xFFFE)

                    if(menufacturerData == null) return

                    val combined = menufacturerData?.let{
                        String(it)
                    }

                    val combined2 = latLng?.let{
                        String(it)
                    }

                    combined?.let{
                        val adParts = it.split("|", limit=3)
                        val scParts = combined2?.split("|", limit=2)

                        if(adParts.size == 3){
                            val timestamp = adParts[0].toLongOrNull() ?: return
                            val depth = adParts[1].toIntOrNull() ?: return
                            val nickname = adParts[2]

                            val lat = scParts?.getOrNull(0)?.toIntOrNull()?: return
                            val lng = scParts?.getOrNull(1)?.toIntOrNull()?: return

                            // 추가
                            userMap[nickname] = NearbyUser(nickname, depth, timestamp, lat, lng)

                            // 여기에 화면에 띄워줘야함
                        }

                        // 릴레이 코드 넣어줘야함
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e("스캔실패", "스캔 실패: $errorCode")
            }
        }


        // scan 등록
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
        }, 1 * 60 * 1000) // 1분



    }

    fun stopScanning(){
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: SecurityException){
            Log.e("권한문제", "하기싷다 ")
        }

        restartHandler.removeCallbacksAndMessages(null)
    }

    // 3초 동안 업데이트가 없으면 제거하는 타이머
    private fun startCleanupTimer(context: Context) {
        handler.postDelayed(object : Runnable {
            override fun run() {
                cleanupOldUsers()
                handler.postDelayed(this, 1000) // Check every 1 second
            }
        }, 1000)
    }

    private fun cleanupOldUsers() {
        val currentTime = System.currentTimeMillis()
        userMap.entries.removeAll { (_, user) ->
            currentTime - user.lastSeen > SCAN_TIMEOUT
        }
    }

}