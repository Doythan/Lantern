package com.ssafy.lantern.data.source.ble.scanner

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ssafy.lantern.data.source.ble.gatt.GattClientManager
import java.util.UUID

class ScannerManager(private val activity: Activity, private val textView: TextView) {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private val gattClientManager = GattClientManager(activity)


    // 객체가 만들어지자 마자 실행 됨
    init {
        val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        Log.d("생성되나요?", "생성")
    }

    fun startScanning(){
        if (bluetoothLeScanner == null) {
            Log.e("BLE", "BluetoothLeScanner is null")
            return
        }

        // 랜턴 UUID
        val SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab")

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID)) // 특정 UUID만 필터링
            .build()

        val scanFilters = listOf(scanFilter)

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // 빠른 반응 모드
            .build()


        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                result?.let { scanResult ->
                    val deviceName = if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED) {
                        scanResult.device.name ?: "Unknown Device"
                    } else {
                        "Unknown Device" // 권한이 없으면 이름 대신 Unknown으로 처리
                    }

                    activity.runOnUiThread {
                        textView.text = "디바이스 발견: $deviceName\n주소: ${scanResult.device.address}"
                    }

                    Log.d("주소", "${scanResult.device.address}")

                    // gatt 연결
                    gattClientManager.connectToDevice(scanResult.device)
                }
                Log.d("스캔성공", "스캔 성공")
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e("스캔실패", "스캔 실패: $errorCode")
            }
        }

        bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
    }
}