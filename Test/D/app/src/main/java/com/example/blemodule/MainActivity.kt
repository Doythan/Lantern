package com.example.blemodule

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.blemodule.data.source.ble.BleScannerManager
import com.example.blemodule.ui.view.components.DeviceAdapter
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var bleScannerManager: BleScannerManager
    private lateinit var deviceAdapter: DeviceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_scan) // ← 주의: layout_scan.xml!

        // BLE 스캐너 매니저 초기화
        bleScannerManager = BleScannerManager(this)

        // RecyclerView 초기화
        val recyclerViewDevices = findViewById<RecyclerView>(R.id.recyclerViewDevices)
        deviceAdapter = DeviceAdapter()
        recyclerViewDevices.layoutManager = LinearLayoutManager(this)
        recyclerViewDevices.adapter = deviceAdapter

        // 버튼 클릭 시 BLE 스캔
        val btnScan = findViewById<MaterialButton>(R.id.btnScan)
        btnScan.setOnClickListener {
            deviceAdapter.clearDevices() // 기존 리스트 비우고
            bleScannerManager.startScan { device ->
                deviceAdapter.addDevice(device) // 스캔된 디바이스 추가
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleScannerManager.stopScan()
    }
}
