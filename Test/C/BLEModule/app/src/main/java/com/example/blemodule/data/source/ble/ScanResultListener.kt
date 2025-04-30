package com.example.blemodule.data.source.ble

import android.bluetooth.BluetoothDevice

// 스캔 결과 전달
interface ScanResultListener {
    fun onDeviceFound(device: BluetoothDevice)
}