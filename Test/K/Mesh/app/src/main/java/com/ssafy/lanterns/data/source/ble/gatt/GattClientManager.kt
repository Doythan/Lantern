package com.ssafy.lanterns.data.source.ble.gatt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import android.bluetooth.BluetoothGattService
import android.os.Handler
import android.os.Looper
import com.ssafy.lanterns.data.source.ble.mesh.MeshMessage
import com.ssafy.lanterns.data.source.ble.mesh.MessageType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * BLE GATT 클라이언트 관리자 클래스
 * 원격 디바이스에 연결하고 메시지를 주고받는 기능 제공
 */
class GattClientManager(
    private val context: Context,
    var onConnectionStateChange: (device: BluetoothDevice, status: Int, newState: Int) -> Unit = { _, _, _ -> },
    var onMessageReceived: (String) -> Unit = { _ -> },
    var onMeshMessageReceived: (MeshMessage) -> Unit = { _ -> }
) {
    private val TAG = "GattClientManager"
    private val connections = ConcurrentHashMap<String, BluetoothGatt>()
    private val handler = Handler(Looper.getMainLooper())
    
    // 서비스 및 특성 UUID
    companion object {
        // 채팅 서비스
        val CHAT_SERVICE_UUID: UUID = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
        val CHAT_CHARACTERISTIC_UUID: UUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")
        
        // 메쉬 네트워크 서비스
        val MESH_SERVICE_UUID: UUID = UUID.fromString("4d61-72b0-4e65-b208-6adff42f5624")
        val MESH_CHARACTERISTIC_UUID: UUID = UUID.fromString("4d61-72b0-4e65-b208-6adff42f5625")
        
        // CCC 디스크립터 UUID
        val CLIENT_CONFIG_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        
        // 최대 연결 유지 시간 (30초)
        const val MAX_CONNECTION_DURATION = 30000L
    }

    /**
     * 디바이스 연결
     * @param device 연결 대상 디바이스
     * @return 연결 성공 여부
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot connect to device.")
            return false
        }
        
        // 이미 연결된 디바이스인지 확인
        if (connections.containsKey(device.address)) {
            Log.d(TAG, "Already connected to ${device.address}")
            return true
        }
        
        try {
            val gatt = device.connectGatt(context, false, gattCallback)
            connections[device.address] = gatt
            
            // 연결 타임아웃 설정
            handler.postDelayed({
                if (connections.containsKey(device.address)) {
                    Log.d(TAG, "Connection timeout for ${device.address}")
                    disconnect(device)
                }
            }, MAX_CONNECTION_DURATION)
            
            Log.d(TAG, "Connecting to ${device.address}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to ${device.address}", e)
            return false
        }
    }
    
    /**
     * 연결 해제
     * @param device 연결 해제할 디바이스
     */
    @SuppressLint("MissingPermission")
    fun disconnect(device: BluetoothDevice) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot disconnect.")
            return
        }
        
        val gatt = connections[device.address] ?: return
        
        try {
            gatt.disconnect()
            gatt.close()
            connections.remove(device.address)
            Log.d(TAG, "Disconnected from ${device.address}")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting from ${device.address}", e)
        }
    }
    
    /**
     * 모든 연결 해제
     */
    @SuppressLint("MissingPermission")
    fun disconnectAll() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot disconnect all connections.")
            return
        }
        
        try {
            // 모든 연결 해제
            connections.forEach { (address, gatt) ->
                gatt.disconnect()
                gatt.close()
                Log.d(TAG, "Disconnected from $address")
            }
            
            // 맵 초기화
            connections.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting all connections", e)
        }
    }
    
    /**
     * GATT 콜백
     */
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val device = gatt.device
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // 연결 성공
                    Log.d(TAG, "Connected to ${device.address}")
                    
                    // 서비스 탐색 시작
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        gatt.discoverServices()
                    } else {
                        Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot discover services.")
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // 연결 해제
                    Log.d(TAG, "Disconnected from ${device.address}")
                    gatt.close()
                    connections.remove(device.address)
                }
            } else {
                // 연결 실패
                Log.e(TAG, "Connection failed with status: $status")
                gatt.close()
                connections.remove(device.address)
            }
            
            // 콜백 호출
            onConnectionStateChange(device, status, newState)
        }
        
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered for ${gatt.device.address}")
                
                // 권한 확인
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot access discovered services.")
                    return
                }
                
                // 채팅 서비스 찾기
                val chatService = gatt.getService(CHAT_SERVICE_UUID)
                if (chatService != null) {
                    // 채팅 특성 찾기
                    val chatCharacteristic = chatService.getCharacteristic(CHAT_CHARACTERISTIC_UUID)
                    if (chatCharacteristic != null) {
                        // 알림 활성화
                        enableNotifications(gatt, chatCharacteristic)
                        Log.d(TAG, "Chat service found and notifications enabled")
                    }
                }
                
                // 메쉬 서비스 찾기
                val meshService = gatt.getService(MESH_SERVICE_UUID)
                if (meshService != null) {
                    // 메쉬 특성 찾기
                    val meshCharacteristic = meshService.getCharacteristic(MESH_CHARACTERISTIC_UUID)
                    if (meshCharacteristic != null) {
                        // 알림 활성화
                        enableNotifications(gatt, meshCharacteristic)
                        Log.d(TAG, "Mesh service found and notifications enabled")
                    }
                }
            } else {
                Log.e(TAG, "Service discovery failed with status: $status")
            }
        }
        
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            try {
                when (characteristic.uuid) {
                    CHAT_CHARACTERISTIC_UUID -> {
                        // 채팅 메시지 처리
                        val messageBytes = characteristic.value
                        val message = String(messageBytes, Charsets.UTF_8)
                        Log.d(TAG, "Received chat message: $message from ${gatt.device.address}")
                        onMessageReceived(message)
                    }
                    MESH_CHARACTERISTIC_UUID -> {
                        // 메쉬 메시지 처리
                        val messageBytes = characteristic.value
                        val meshMessage = MeshMessage.fromBytes(messageBytes)
                        if (meshMessage != null) {
                            Log.d(TAG, "Received mesh message: sender=${meshMessage.sender}, seq=${meshMessage.sequenceNumber}")
                            onMeshMessageReceived(meshMessage)
                        } else {
                            Log.e(TAG, "Failed to parse mesh message")
                        }
                    }
                    else -> {
                        Log.w(TAG, "Unknown characteristic changed: ${characteristic.uuid}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing characteristic change", e)
            }
        }
    }
    
    /**
     * 특성 알림 활성화
     * @param gatt GATT 클라이언트
     * @param characteristic 활성화할 특성
     */
    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot enable notifications.")
            return false
        }
        
        try {
            // 알림 활성화
            val success = gatt.setCharacteristicNotification(characteristic, true)
            if (!success) {
                Log.e(TAG, "Failed to enable notifications for ${characteristic.uuid}")
                return false
            }
            
            // CCC 디스크립터 찾기
            val descriptor = characteristic.getDescriptor(CLIENT_CONFIG_DESCRIPTOR_UUID)
            if (descriptor != null) {
                // 디스크립터 값 설정
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                return gatt.writeDescriptor(descriptor)
            } else {
                Log.e(TAG, "CCC descriptor not found for ${characteristic.uuid}")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling notifications", e)
            return false
        }
    }
    
    /**
     * 채팅 메시지 전송
     * @param device 대상 디바이스
     * @param message 전송할 메시지
     * @return 전송 성공 여부
     */
    @SuppressLint("MissingPermission")
    fun sendMessage(device: BluetoothDevice, message: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot send message.")
            return false
        }
        
        val gatt = connections[device.address]
        if (gatt == null) {
            Log.e(TAG, "Device not connected: ${device.address}")
            return false
        }
        
        try {
            // 채팅 서비스 찾기
            val chatService = gatt.getService(CHAT_SERVICE_UUID)
            if (chatService == null) {
                Log.e(TAG, "Chat service not found")
                return false
            }
            
            // 채팅 특성 찾기
            val chatCharacteristic = chatService.getCharacteristic(CHAT_CHARACTERISTIC_UUID)
            if (chatCharacteristic == null) {
                Log.e(TAG, "Chat characteristic not found")
                return false
            }
            
            // 메시지 설정 및 쓰기
            val messageBytes = message.toByteArray(Charsets.UTF_8)
            chatCharacteristic.value = messageBytes
            
            return gatt.writeCharacteristic(chatCharacteristic)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message to ${device.address}", e)
            return false
        }
    }
    
    /**
     * 메쉬 메시지 전송
     * @param device 대상 디바이스
     * @param message 전송할 메시지
     * @return 전송 성공 여부
     */
    @SuppressLint("MissingPermission")
    fun sendMeshMessage(device: BluetoothDevice, message: MeshMessage): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot send mesh message.")
            return false
        }
        
        val gatt = connections[device.address]
        if (gatt == null) {
            Log.e(TAG, "Device not connected: ${device.address}")
            return false
        }
        
        try {
            // 메쉬 서비스 찾기
            val meshService = gatt.getService(MESH_SERVICE_UUID)
            if (meshService == null) {
                Log.e(TAG, "Mesh service not found")
                return false
            }
            
            // 메쉬 특성 찾기
            val meshCharacteristic = meshService.getCharacteristic(MESH_CHARACTERISTIC_UUID)
            if (meshCharacteristic == null) {
                Log.e(TAG, "Mesh characteristic not found")
                return false
            }
            
            // 메시지 직렬화 및 쓰기
            val messageBytes = message.toBytes()
            meshCharacteristic.value = messageBytes
            
            return gatt.writeCharacteristic(meshCharacteristic)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending mesh message to ${device.address}", e)
            return false
        }
    }
}