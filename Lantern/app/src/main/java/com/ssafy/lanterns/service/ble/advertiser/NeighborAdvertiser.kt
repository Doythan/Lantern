package com.ssafy.lanterns.service.ble.advertiser

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
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
import java.util.UUID

object NeighborAdvertiser{
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback : AdvertiseCallback? = null

    fun init(activity: Activity) {
        val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
    }

    fun startAdvertising(nickname: String, time:String, lat:Double, lng:Double, state: Int,  activity: Activity){
        stopAdvertising()

        var advertiseData: AdvertiseData?= null
        var scanResponseData : AdvertiseData ?= null

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        // 최초로 데이터를 보낼 때
        if(state == 0){
//            val time = System.currentTimeMillis().toString()
            val depth = "1"
            val adCombined = "$time|$depth|$nickname"
            val adBytes = adCombined.toByteArray()

            // 좌표
            val latInt = (lat * 1e6).toInt()
            val lngInt = (lng * 1e6).toInt()

            val scCombined = "$latInt|$lngInt"
            val scBytes = scCombined.toByteArray()


            // 중복 처리 넣어줘야함

            // packet
            advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addManufacturerData(0xFFFF, adBytes)
                .build()

            scanResponseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addManufacturerData(0xFFFE, scBytes)
                .build()

            // advertise 성공 실패 코드
            advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    super.onStartSuccess(settingsInEffect)
                    Log.d("onStartSuccess", "${nickname}")
                }

                override fun onStartFailure(errorCode: Int) {
                    super.onStartFailure(errorCode)
                    Log.d("실패?", "${errorCode}")
                }
            }

            // advertising 등록
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED)
                bluetoothLeAdvertiser?.startAdvertising(settings, scanResponseData, advertiseData, advertiseCallback)
        }
    }



    // advertising 끊기
    fun stopAdvertising(){
        advertiseCallback?.let{
            try {
                bluetoothLeAdvertiser?.stopAdvertising(it)
            } catch (e: SecurityException){
                Log.e("권한문제", "하기싷다 ")
            }
        }

        advertiseCallback = null
    }
}