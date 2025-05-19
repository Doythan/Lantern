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

    // 랜턴 앱 전용 상수
    private const val LANTERN_MANUFACTURER_ID_MESSAGE = 0xFFFF
    private const val LANTERN_MANUFACTURER_ID_EMAIL = 0xFFFE
    private val LANTERN_APP_UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab")

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

        // 최초로 데이터를 보낼 때
        if(state == 0) {
            // 메시지 고유 UUID 생성 (8자리 16진수)
            val uuid = UUID.randomUUID().toString().substring(0, 8)
            val adString = messageList.getOrNull(0) ?: ""
            val scString = messageList.getOrNull(1) ?: ""
            Log.d("AdvertiserManager", "메시지 전송: ${adString}, ${scString}")
            val adCombined = "$uuid|$adString"
            val scCombined = "$email|$scString"
            val adBytes = adCombined.toByteArray()
            val scBytes = scCombined.toByteArray()

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
            advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addManufacturerData(LANTERN_MANUFACTURER_ID_MESSAGE, messageList.getOrNull(0)?.toByteArray())
                .build()

            scanResponseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addManufacturerData(LANTERN_MANUFACTURER_ID_EMAIL, messageList.getOrNull(1)?.toByteArray())
                .build()
        }

        // 광고 콜백
        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                super.onStartSuccess(settingsInEffect)
                Log.d("AdvertiserManager", "광고 시작 성공: ${messageList}")
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                Log.e("AdvertiserManager", "광고 시작 실패: 에러 코드 ${errorCode}")
            }
        }
        
        // 권한 확인 후 광고 시작
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED) {
            bluetoothLeAdvertiser?.startAdvertising(settings, advertiseData, scanResponseData, advertiseCallback)
            
            // 광고는 30초 후 자동 중단 (전력 소모 방지)
            restartHandler.postDelayed({
                stopAdvertising()
            }, 30 * 1000) // 30초
        } else {
            Log.e("BLE", "BLUETOOTH_ADVERTISE 권한이 없습니다. Advertising을 시작할 수 없습니다.")
        }
    }

    // 광고 중지 함수
    fun stopAdvertising(){
        advertiseCallback?.let{
            try {
                bluetoothLeAdvertiser?.stopAdvertising(it)
                Log.d("AdvertiserManager", "광고 중지")
            } catch (e: SecurityException){
                Log.e("AdvertiserManager", "광고 중지 실패: ${e.message}")
            }
        }

        advertiseCallback = null
        restartHandler.removeCallbacksAndMessages(null) // 재시작 타이머 제거
    }
}