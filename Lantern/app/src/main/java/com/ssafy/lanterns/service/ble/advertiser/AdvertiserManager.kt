package com.ssafy.lanterns.service.ble.advertiser

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.Manifest
import android.bluetooth.BluetoothManager
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




    fun init(activity: Activity) {
        val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
    }

    fun startAdvertising(messageList: List<String>, email: String, activity: Activity, state: Int){
        stopAdvertising()
        // Advertise Setting
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        var advertiseData: AdvertiseData ?= null
        var scanResponseData : AdvertiseData ?= null

        // 랜턴 UUID
//        val SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab")



        var combined : String = "init"
        // 최초로 데이터를 보낼 때
        if(state == 0) {
            // message의 uuid
            val uuid = UUID.randomUUID().toString().substring(0, 8)
            val adString = messageList.getOrNull(0) ?: ""
            val scString = messageList.getOrNull(1) ?: ""
            Log.d("adString", "${adString}, ${scString}")
            val adCombined = "$uuid|$adString"
            val scCombined = "$email|$scString"
            val adBytes = adCombined.toByteArray()
            val scBytes = scCombined.toByteArray()


            // 중복 처리
            ScannerManager.updateChatSet(uuid, adCombined + scCombined, activity)

            advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addManufacturerData(0xFFFF, adBytes)
                .build()

            scanResponseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addManufacturerData(0xFFFE, scBytes)
                .build()
        }
        // 릴레이인 경우
        else{
            advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addManufacturerData(0xFFFF, messageList.getOrNull(0)?.toByteArray())
                .build()

            scanResponseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addManufacturerData(0xFFFE, messageList.getOrNull(1)?.toByteArray())
                .build()
        }

        val data = email.toByteArray()

        // scan 응답 데이터
        // email 값

        // Advertising 시 소량의 데이터
//            val advertiseData = AdvertiseData.Builder()
//                .setIncludeDeviceName(false)
//                .addManufacturerData(0xFFFF, data)
//                .build()

        // advertise 성공 실패 코드
        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                super.onStartSuccess(settingsInEffect)
                Log.d("onStartSuccess", "${messageList}")
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                Log.d("실패?", "${errorCode}")
            }
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED) {
            bluetoothLeAdvertiser?.startAdvertising(settings, advertiseData, scanResponseData,advertiseCallback)
            // 재시작
//            restartHandler.postDelayed({
//                Log.d("광고 재시작", "${lastMessage}, ${lastState}")
//                if(state == 0)
//                    startAdvertising(combined,email ,activity, state)
//                else startAdvertising(message,email ,activity, state)
//            }, 1 * 60 * 1000)

        } else {
            Log.e("BLE", "BLUETOOTH_ADVERTISE 권한이 없습니다. Advertising을 시작할 수 없습니다.")
        }
    }

    // 아까 보냈던거 다시 안보내려고
    // stop 을 해줘야됨
    fun stopAdvertising(){
        advertiseCallback?.let{
            try {
                bluetoothLeAdvertiser?.stopAdvertising(it)
            } catch (e: SecurityException){
                Log.e("권한문제", "하기싷다 ")
            }
        }

        advertiseCallback = null
        restartHandler.removeCallbacksAndMessages(null) // 재시작 제거
    }
}