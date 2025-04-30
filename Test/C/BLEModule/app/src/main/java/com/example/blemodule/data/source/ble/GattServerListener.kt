package com.example.blemodule.data.source.ble

import android.bluetooth.BluetoothDevice

interface GattServerListener {
    fun onWrite(device: BluetoothDevice, data: ByteArray)
}