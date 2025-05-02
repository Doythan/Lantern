package com.example.blemodule.data.ble.advertiser

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
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID

class AdvertiserManager(private val activity: Activity){
        private var bluetoothAdapter: BluetoothAdapter? = null
        private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
        private var advertiseCallback : AdvertiseCallback? = null

        init {
            val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothAdapter = bluetoothManager?.adapter
            bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        }

        fun startAdvertising(){
            // Advertise Setting
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build()

            // 랜턴 UUID
            val SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab")

            // Advertising 시 소량의 데이터
            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()

            // advertise 성공 실패 코드
            advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    super.onStartSuccess(settingsInEffect)
                    Log.d("생성되나요?", "생성")
                }

                override fun onStartFailure(errorCode: Int) {
                    super.onStartFailure(errorCode)
                    Log.d("실패?", "${errorCode}")
                }
            }
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED) {
                bluetoothLeAdvertiser?.startAdvertising(settings, advertiseData, advertiseCallback)
            } else {
                Log.e("BLE", "BLUETOOTH_ADVERTISE 권한이 없습니다. Advertising을 시작할 수 없습니다.")
            }
        }
}