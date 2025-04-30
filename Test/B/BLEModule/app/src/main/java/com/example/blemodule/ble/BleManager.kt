package com.example.blemodule.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * BLE 통신을 관리하는 클래스
 */
class BleManager(private val context: Context, private val deviceName: String) {
    private val TAG = "BleManager"
    
    // BLE 관련 객체
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    
    // BLE 서비스 및 특성 UUID
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB") // 임의의 서비스 UUID
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB") // 임의의 특성 UUID
    }
    
    // BLE 서버 및 클라이언트 관리
    private val bleServer = BleServer(context, this)
    private val bleClient = BleClient(context, this)
    private val bleScanner = BleScanner(context, this)
    private val bleAdvertiser = BleAdvertiser(context)
    
    // 연결된 GATT 클라이언트 관리
    private val connectedGattClients = ConcurrentHashMap<String, BluetoothGatt>()
    
    // 연결된 기기 목록
    private val connectedDevices = ConcurrentHashMap<String, String>() // deviceAddress -> deviceName
    
    // 메시지 및 기기 목록 Flow
    private val _messageFlow = MutableSharedFlow<BleMessage>(replay = 0)
    val messageFlow: SharedFlow<BleMessage> = _messageFlow
    
    private val _connectedDevicesFlow = MutableSharedFlow<List<String>>(replay = 1)
    val connectedDevicesFlow: SharedFlow<List<String>> = _connectedDevicesFlow
    
    // 코루틴 스코프
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // 블루투스 권한 확인
    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    // 서비스 시작
    fun startService() {
        Log.d(TAG, "BLE 서비스 시작: $deviceName")
        
        // 권한 확인
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "블루투스 권한이 없습니다.")
            return
        }
        
        // GATT 서버 시작
        bleServer.startServer()
        
        // BLE 광고 시작
        bleAdvertiser.startAdvertising(bluetoothAdapter, SERVICE_UUID)
        
        // BLE 스캔 시작
        bleScanner.startScanning(bluetoothAdapter, SERVICE_UUID)
        
        // 연결된 기기 목록 초기화
        updateConnectedDevices()
    }
    
    // 서비스 중지
    fun stopService() {
        Log.d(TAG, "BLE 서비스 중지")
        
        // 권한 확인
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "블루투스 권한이 없습니다.")
            return
        }
        
        // BLE 스캔 중지
        bleScanner.stopScanning()
        
        // BLE 광고 중지
        bleAdvertiser.stopAdvertising()
        
        // GATT 서버 중지
        bleServer.stopServer()
        
        // 연결된 GATT 클라이언트 연결 해제
        disconnectAllClients()
    }
    
    // 메시지 전송
    fun sendMessage(content: String) {
        // 권한 확인
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "블루투스 권한이 없습니다.")
            return
        }
        
        val message = BleMessage(
            senderName = deviceName,
            content = content,
            messageType = MessageType.CHAT
        )
        
        // 메시지 로컬 처리
        handleReceivedMessage(message)
        
        // 연결된 모든 기기에 메시지 전송
        broadcastMessage(message)
    }
    
    // 모든 연결된 기기에 메시지 브로드캐스트
    private fun broadcastMessage(message: ByteArray) {
        // 권한 확인
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "블루투스 권한이 없습니다.")
            return
        }
        
        connectedGattClients.values.forEach { gatt ->
            BleClient.sendData(gatt, message)
        }
    }
    
    private fun broadcastMessage(message: BleMessage) {
        broadcastMessage(message.toByteArray())
    }
    
    // 수신된 메시지 처리
    fun handleReceivedMessage(message: BleMessage) {
        Log.d(TAG, "메시지 수신: ${message.senderName}: ${message.content}")
        scope.launch {
            _messageFlow.emit(message)
        }
    }
    
    // GATT 클라이언트 추가
    fun addGattClient(device: BluetoothDevice, gatt: BluetoothGatt) {
        // 권한 확인
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "블루투스 권한이 없습니다.")
            return
        }
        
        Log.d(TAG, "GATT 클라이언트 추가: ${device.address}")
        connectedGattClients[device.address] = gatt
        
        val deviceNameStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                device.name ?: "Unknown Device"
            } else {
                "Unknown Device"
            }
        } else {
            device.name ?: "Unknown Device"
        }
        
        connectedDevices[device.address] = deviceNameStr
        updateConnectedDevices()
    }
    
    // GATT 클라이언트 제거
    fun removeGattClient(deviceAddress: String) {
        // 권한 확인
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "블루투스 권한이 없습니다.")
            return
        }
        
        Log.d(TAG, "GATT 클라이언트 제거: $deviceAddress")
        connectedGattClients.remove(deviceAddress)?.close()
        connectedDevices.remove(deviceAddress)
        updateConnectedDevices()
    }
    
    // 모든 GATT 클라이언트 연결 해제
    private fun disconnectAllClients() {
        // 권한 확인
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "블루투스 권한이 없습니다.")
            return
        }
        
        Log.d(TAG, "모든 GATT 클라이언트 연결 해제")
        connectedGattClients.forEach { (_, gatt) ->
            gatt.disconnect()
            gatt.close()
        }
        connectedGattClients.clear()
        connectedDevices.clear()
        updateConnectedDevices()
    }
    
    // 연결된 GATT 클라이언트 목록 반환
    fun getConnectedGattClients(): Map<String, BluetoothGatt> {
        return connectedGattClients
    }
    
    // 연결된 기기 목록 업데이트
    private fun updateConnectedDevices() {
        val deviceNames = connectedDevices.values.toList()
        scope.launch {
            _connectedDevicesFlow.emit(deviceNames)
        }
    }
}
