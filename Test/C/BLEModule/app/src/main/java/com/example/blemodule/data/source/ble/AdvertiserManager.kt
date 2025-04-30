package com.example.blemodule.data.source.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.example.blemodule.utils.Constants

// 광고 코드
class AdvertiserManager(private val context: Context) {

    companion object {
        private const val TAG = "AdvertiserManager"
    }

    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(BluetoothManager::class.java)
    }
    private val advertiser by lazy {
        bluetoothManager.adapter.bluetoothLeAdvertiser
    }


    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "Advertising 시작 성공.")
        }
        override fun onStartFailure(errorCode: Int) {
            Log.d(TAG, "Advertising 시작 실패: $errorCode")
            handleAdvertiseError(errorCode)
        }
    }

    /**
     * 광고 시작
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun startAdvertising() {
        if (!hasAdvertisePermission()) {
            Log.w(TAG, "BLUETOOTH_ADVERTISE 권한 없음")
            return
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(Constants.MESH_SERVICE_UUID)
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
            ?: Log.w(TAG, "Advertiser null, Advertising 불가.")
    }

    /**
     * 광고 중지
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun stopAdvertising() {
        if (!hasAdvertisePermission()) return
        try {
            advertiser?.stopAdvertising(advertiseCallback)
            Log.d(TAG, "Advertising 중지 요청됨.")
        } catch (e: Exception) {
            Log.e(TAG, "Advertising 중지 실패: ${e.message}", e)
        }
    }

    /**
     * 광고 실패 코드별 처리
     */
    private fun handleAdvertiseError(errorCode: Int) {
        when (errorCode) {
            AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE ->
                Log.e(TAG, "오류: Advertise 데이터 크기 초과 (31 Bytes).")
            AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS ->
                Log.e(TAG, "오류: 시스템 Advertiser 개수 제한 초과.")
            AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED ->
                Log.i(TAG, "정보: Advertising 이미 시작됨.")
            AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR ->
                Log.e(TAG, "오류: Advertising 내부 오류.")
            AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED ->
                Log.e(TAG, "오류: 기기가 Advertising 미지원.")
            else ->
                Log.e(TAG, "알 수 없는 Advertising 오류: $errorCode")
        }
    }

    private fun hasAdvertisePermission(): Boolean =
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_ADVERTISE
        ) == PackageManager.PERMISSION_GRANTED
}
