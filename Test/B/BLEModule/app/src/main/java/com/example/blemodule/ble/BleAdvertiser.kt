package com.example.blemodule.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

/**
 * BLE 광고를 담당하는 클래스
 * 다른 기기가 이 기기를 발견할 수 있도록 BLE 광고를 수행
 */
class BleAdvertiser(private val context: Context) {
    private val TAG = "BleAdvertiser"
    
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var advertising = false
    
    // BLE 광고 시작
    fun startAdvertising(bluetoothAdapter: BluetoothAdapter, serviceUuid: UUID) {
        if (advertising) {
            return
        }
        
        Log.d(TAG, "BLE 광고 시작")
        
        // BLE 광고자 가져오기
        bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        
        // 광고 설정
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()
        
        // 광고 데이터 (서비스 UUID 포함)
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(serviceUuid))
            .build()
        
        // 응답 데이터 (빈 데이터)
        val scanResponse = AdvertiseData.Builder()
            .build()
        
        // 광고 시작
        bluetoothLeAdvertiser?.startAdvertising(settings, data, scanResponse, advertiseCallback)
        advertising = true
    }
    
    // BLE 광고 중지
    fun stopAdvertising() {
        if (!advertising) {
            return
        }
        
        Log.d(TAG, "BLE 광고 중지")
        
        bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        advertising = false
    }
    
    // 광고 콜백
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "광고 시작 성공")
        }
        
        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "광고 시작 실패: $errorCode")
            advertising = false
        }
    }
}
