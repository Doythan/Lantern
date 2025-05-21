package com.ssafy.lanterns.data.source.ble.advertiser

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import java.util.UUID

private val SERVICE_UUID = UUID.fromString("0000beef-0000-1000-8000-00805f9b34fb")

@SuppressLint("MissingPermission")
class AudioAdvertiserManager(private val context: Context) {
    private val advertiser: BluetoothLeAdvertiser? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter
            ?.bluetoothLeAdvertiser
    }
    private var callback: AdvertiseCallback? = null
    private var advertising = false
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private val MAX_RETRY_COUNT = 5

    @SuppressLint("MissingPermission")
    fun startAdvertising() {
        if (advertising) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val adv = advertiser ?: return

        callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settings: AdvertiseSettings) {
                advertising = true
                retryCount = 0
            }

            override fun onStartFailure(error: Int) {
                advertising = false
                if (retryCount < MAX_RETRY_COUNT) {
                    retryCount++
                    handler.postDelayed({ startAdvertising() }, 2000)
                }
            }
        }

        try {
            adv.startAdvertising(
                AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                    .setConnectable(true)
                    .build(),
                AdvertiseData.Builder()
                    .addServiceUuid(ParcelUuid(SERVICE_UUID))
                    .build(),
                AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .build(),
                callback
            )
        } catch (e: Exception) {
            advertising = false
            if (retryCount < MAX_RETRY_COUNT) {
                retryCount++
                handler.postDelayed({ startAdvertising() }, 2000)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        if (!advertising || callback == null) return

        val adv = advertiser
        if (adv == null || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            advertising = false
            callback = null
            retryCount = 0
            return
        }

        try {
            adv.stopAdvertising(callback)
        } finally {
            advertising = false
            callback = null
            retryCount = 0
        }
    }

    fun isAdvertising(): Boolean {
        return advertising
    }

    fun checkAdvertisingStatus() {
        if (!advertising && retryCount < MAX_RETRY_COUNT) {
            startAdvertising()
        }
    }
}