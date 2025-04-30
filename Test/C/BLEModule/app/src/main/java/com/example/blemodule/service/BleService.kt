package com.example.blemodule.service

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.example.blemodule.data.source.ble.AdvertiserManager
import com.example.blemodule.data.source.ble.GattClientListener
import com.example.blemodule.data.source.ble.GattClientManager
import com.example.blemodule.data.source.ble.GattServerListener
import com.example.blemodule.data.source.ble.GattServerManager
import com.example.blemodule.data.source.ble.ScanResultListener
import com.example.blemodule.data.source.ble.ScannerManager
import com.example.blemodule.utils.Constants
import java.nio.charset.StandardCharsets

class BleService(
    private val context: Context
) : ScanResultListener,
    GattServerListener,
    GattClientListener {

    companion object {
        private const val TAG = "BleService"
    }

    // 1) BLE GATT 콜백: 상태 변화→리스너로 위임
    private val bleGattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            val device = gatt.device
            Log.d(TAG, "GATT 상태 변경: $newState (status=$status) for ${device.address}")
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    onConnected(device)           // GattClientListener 구현부로
                    gatt.discoverServices()
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    onDisconnected(device)        // GattClientListener 구현부로
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val device = gatt.device
            Log.d(TAG, "서비스 발견: status=$status for ${device.address}")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onServicesDiscovered(device)     // GattClientListener 구현부로
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            // 데이터 수신 → GattClientListener 구현부로 위임
            onCharacteristicChanged(gatt.device, characteristic.value)
        }
    }

    // 2) BLE 구성 요소
    private val advertiser = AdvertiserManager(context)
    private val server     = GattServerManager(context, listener = this)
    private val client     = GattClientManager(context, listener = this)
    private val scanner    = ScannerManager(
        context       = context,
        gattCallback  = bleGattCallback,
        listener      = this
    )

    // 3) 연결된 피어 추적
    private val knownServers = mutableSetOf<BluetoothDevice>()
    private val knownClients = mutableSetOf<BluetoothDevice>()

    // 4) UI 메시지 콜백
    private var appMessageListener: ((String, String) -> Unit)? = null
    fun setAppMessageListener(listener: (sourceId: String, msg: String) -> Unit) {
        appMessageListener = listener
    }

    private var isRunning = false
    private var myDeviceId = ""

    @SuppressLint("MissingPermission")
    @RequiresPermission(allOf = [
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    ])
    fun startService(deviceId: String) {
        if (isRunning) return
        myDeviceId = deviceId
        server.startServer()
        advertiser.startAdvertising()
        scanner.startScanning()
        isRunning = true
        Log.d(TAG, "BLE Service started (ID=$deviceId)")
    }

    fun sendAppMessage(targetId: String, payload: String) {
        if (!isRunning) return
        val bytes = buildString {
            append(Constants.MSG_TYPE_APP)
            append(Constants.MSG_TYPE_DELIMITER)
            append(targetId)
            append(Constants.PAYLOAD_DELIMITER)
            append(myDeviceId)
            append(Constants.PAYLOAD_DELIMITER)
            append(payload)
        }.toByteArray(StandardCharsets.UTF_8)
        writeToAllNeighbors(bytes)
        Log.d(TAG, "APP 메시지 전송: $payload -> $targetId")
    }

    @SuppressLint("MissingPermission")
    fun stopService() {
        if (!isRunning) return
        scanner.stopScanning()
        advertiser.stopAdvertising()
        server.stopServer()
        client.disconnect()
        knownServers.clear()
        knownClients.clear()
        isRunning = false
        Log.d(TAG, "BLE Service stopped")
    }

    // ====================================================
    // ScanResultListener 구현
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDeviceFound(device: BluetoothDevice) {
        Log.d(TAG, "장치 발견: ${device.address}")
        // 발견 즉시 GATT 클라이언트 연결
        client.connect(device)
    }

    // GattServerListener 구현 (서버 측에서 write 요청이 들어올 때)
    override fun onWrite(device: BluetoothDevice, data: ByteArray) {
        knownServers.add(device)
        Log.d(TAG, "onWrite from server ← ${device.address}, knownServers=$knownServers")
        val text = String(data, StandardCharsets.UTF_8)
        if (text.startsWith(Constants.MSG_TYPE_APP)) {
            text.split(Constants.MSG_TYPE_DELIMITER, limit = 2)
                .getOrNull(1)
                ?.split(Constants.PAYLOAD_DELIMITER, limit = 3)
                ?.let { p ->
                    if (p.size == 3 && p[0] == myDeviceId) {
                        appMessageListener?.invoke(p[1], p[2])
                    }
                }
        }
    }

    // GattClientListener 구현 (클라이언트 측 콜백)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onConnected(device: BluetoothDevice) {
        knownClients.add(device)
        Log.d(TAG, "Client connected → knownClients=$knownClients")
        client.enableNotifications(device)
    }
    override fun onDisconnected(device: BluetoothDevice) {
        knownClients.remove(device)
        Log.d(TAG, "Client disconnected: ${device.address}")
    }
    override fun onServicesDiscovered(device: BluetoothDevice) {
        Log.d(TAG, "Services discovered: ${device.address}")
    }
    override fun onCharacteristicChanged(device: BluetoothDevice, data: ByteArray) {
        val text = String(data, StandardCharsets.UTF_8)
        Log.d(TAG, "Notify 수신 from ${device.address}: $text")
        // 필요시 UI로 전달
    }
    override fun onCharacteristicWrite(device: BluetoothDevice, status: Int) {
        Log.d(TAG, "Characteristic write complete to ${device.address}: status=$status")
    }

    // ====================================================
    @SuppressLint("MissingPermission")
    private fun writeToAllNeighbors(bytes: ByteArray) {
        Log.d(TAG, "▶ writeToAllNeighbors 호출, knownClients=$knownClients, knownServers=$knownServers")

        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "writeToAllNeighbors: BLUETOOTH_CONNECT 권한 없음")
            return
        }

        // (1) 클라이언트로 연결된 서버들에 쓰기
        knownClients.forEach { device ->
            val text = String(bytes, StandardCharsets.UTF_8)
            Log.d(TAG, "  → client.write 호출: device=${device.address}, data=$text")
            client.write(device, bytes)
        }

        // (2) 서버로 연결된 클라이언트들에 Notify
        knownServers.forEach { device ->
            val text = String(bytes, StandardCharsets.UTF_8)
            Log.d(TAG, "  → server.notifyDevice 호출: device=${device.address}, data=$text")
            server.notifyDevice(device, bytes)
        }
    }
}
