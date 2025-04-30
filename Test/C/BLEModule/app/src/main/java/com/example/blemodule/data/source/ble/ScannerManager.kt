package com.example.blemodule.data.source.ble

import android.Manifest
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.example.blemodule.utils.Constants

class ScannerManager(
    private val context: Context,
    private val gattCallback: BluetoothGattCallback,
    private val listener: ScanResultListener
) {
    companion object {
        private const val TAG = "ScannerManager"
    }

    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(BluetoothManager::class.java)
    }
    private val scanner by lazy {
        bluetoothManager.adapter.bluetoothLeScanner
    }

    // 스캔 시작
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScanning() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            Log.w(TAG, "Scanning 권한 없음")
            return
        }
        val filter = ScanFilter.Builder()
            .setServiceUuid(Constants.MESH_SERVICE_UUID)
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(listOf(filter), settings, scanCallback)
            ?: Log.w(TAG, "Scanner null, Scanning 불가.")
        Log.d(TAG, "Scanning 시작됨 (UUID=${Constants.MESH_SERVICE_UUID})")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScanning() {
        scanner?.stopScan(scanCallback)
        Log.d(TAG, "Scanning 중지됨")
    }

    // 스캐너 콜백
    private val scanCallback = object : ScanCallback() {
        var isConnecting = false

        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.d("ScanCB", "scanResult: serviceUuids=${result.scanRecord?.serviceUuids}")
            if (isConnecting) return
            isConnecting = true

            // BLUETOOTH_CONNECT 권한 체크
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                Log.w(TAG, "Connect 권한 없음")
                return
            }

            stopScanning()

            // SecurityException 대비
            try {
                result.device.connectGatt(context, false, gattCallback)
            } catch (e: SecurityException) {
                Log.e(TAG, "connectGatt 호출 중 SecurityException 발생", e)
                isConnecting = false
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan 실패: $errorCode")
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
