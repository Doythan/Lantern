package com.example.blemodule.data.source.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.blemodule.utils.PermissionHelper

class BleScannerManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        bluetoothManager.adapter
    }

    private val bluetoothLeScanner: BluetoothLeScanner? by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false
    private val scanPeriod: Long = 10000L // 10초

    private var onDeviceFound: ((BluetoothDevice) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startScan(onDeviceFound: (BluetoothDevice) -> Unit) {
        if (!PermissionHelper.hasBluetoothScanPermission(context)) {
            Log.e(TAG, "BLUETOOTH_SCAN permission not granted. Cannot start scan.")
            return
        }

        this.onDeviceFound = onDeviceFound

        if (!isScanning) {
            try {
                bluetoothLeScanner?.startScan(null, buildScanSettings(), scanCallback)
                isScanning = true
                Log.d(TAG, "BLE Scan started")

                handler.postDelayed({
                    stopScan()
                }, scanPeriod)
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException during scan start: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!PermissionHelper.hasBluetoothScanPermission(context)) {
            Log.e(TAG, "BLUETOOTH_SCAN permission not granted. Cannot stop scan.")
            return
        }

        if (isScanning) {
            try {
                bluetoothLeScanner?.stopScan(scanCallback)
                isScanning = false
                Log.d(TAG, "BLE Scan stopped")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException during scan stop: ${e.message}")
            }
        }
    }

    private fun buildScanSettings(): ScanSettings {
        return ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
    }

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.d(TAG, "==== Raw Scan Result ====")
            Log.d(TAG, "Device Name: ${result.device.name ?: "null"}")
            Log.d(TAG, "Device Address: ${result.device.address ?: "null"}")
            Log.d(TAG, "Device BondState: ${result.device.bondState}")
            Log.d(TAG, "Device Type: ${result.device.type}")
            Log.d(TAG, "RSSI: ${result.rssi}")
            Log.d(TAG, "=================================")

            if (PermissionHelper.hasBluetoothScanPermission(context)) {
                onDeviceFound?.invoke(result.device)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (result in results) {
                Log.d(TAG, "[BATCH] ==== Raw Scan Result ====")
                Log.d(TAG, "Device Name: ${result.device.name ?: "null"}")
                Log.d(TAG, "Device Address: ${result.device.address ?: "null"}")
                Log.d(TAG, "Device BondState: ${result.device.bondState}")
                Log.d(TAG, "Device Type: ${result.device.type}")
                Log.d(TAG, "RSSI: ${result.rssi}")
                Log.d(TAG, "=================================")

                if (PermissionHelper.hasBluetoothScanPermission(context)) {
                    onDeviceFound?.invoke(result.device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed with error code: $errorCode")
        }
    }


    companion object {
        private const val TAG = "BleScannerManager"
    }
}
