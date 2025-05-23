package com.ssafy.lanterns.data.source.ble.gatt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattDescriptor
import com.ssafy.lanterns.data.source.ble.mesh.MeshMessage
import com.ssafy.lanterns.data.source.ble.mesh.MessageType
import com.ssafy.lanterns.data.source.ble.mesh.MessageUtils
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * BLE GATT 서버 관리자 클래스
 * 광고 크기 제한(31바이트)을 초과하는 대용량 메시지 전송을 위한 GATT 서버 기능 제공
 */
class GattServerManager(
    private val context: Context,
    var onConnectionStateChange: (device: BluetoothDevice, status: Int, newState: Int) -> Unit = { _, _, _ -> },
    var onClientSubscribed: (device: BluetoothDevice) -> Unit = { _ -> },
    var onClientUnsubscribed: (device: BluetoothDevice) -> Unit = { _ -> },
    var onMeshMessageReceived: (MeshMessage) -> Unit = { _ -> }
) {
    private var gattServer: BluetoothGattServer? = null
    private val connectedClients = ConcurrentHashMap<String, BluetoothDevice>()
    private var chatCharacteristic: BluetoothGattCharacteristic? = null
    private var meshCharacteristic: BluetoothGattCharacteristic? = null
    private var isServerOpen = false
    private val TAG = "GattServerManager"

    // 서비스 및 특성 UUID
    companion object {
        // 기존 채팅 서비스
        val CHAT_SERVICE_UUID: UUID = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
        val CHAT_CHARACTERISTIC_UUID: UUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")
        
        // 메쉬 네트워크 서비스
        val MESH_SERVICE_UUID: UUID = UUID.fromString("4d61-72b0-4e65-b208-6adff42f5624")
        val MESH_CHARACTERISTIC_UUID: UUID = UUID.fromString("4d61-72b0-4e65-b208-6adff42f5625")
        
        // CCC 디스크립터 UUID
        val CLIENT_CONFIG_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    @SuppressLint("MissingPermission")
    fun openGattServer(){
        if (isServerOpen) {
            Log.d(TAG, "GATT Server is already open.")
            return
        }
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (bluetoothManager == null) {
            Log.e(TAG, "BluetoothManager not found.")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            try {
                gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
                addChatService()
                addMeshService()
                isServerOpen = true
                Log.i(TAG, "GATT Server opened successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open GATT server", e)
                isServerOpen = false
            }
        } else {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot open GATT server.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun addChatService() {
        if (gattServer == null) {
             Log.e(TAG, "GATT Server is null, cannot add service.")
             return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot add service.")
            return
        }

        val service = BluetoothGattService(CHAT_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        chatCharacteristic = BluetoothGattCharacteristic(
            CHAT_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val cccDescriptor = BluetoothGattDescriptor(
            CLIENT_CONFIG_DESCRIPTOR_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        chatCharacteristic?.addDescriptor(cccDescriptor)

        service.addCharacteristic(chatCharacteristic)

        try {
            gattServer?.addService(service)
            Log.i(TAG, "Chat service added successfully.")
        } catch (e: Exception) {
             Log.e(TAG, "Failed to add chat service", e)
        }
    }
    
    /**
     * 메쉬 네트워크 서비스 추가
     */
    @SuppressLint("MissingPermission")
    private fun addMeshService() {
        if (gattServer == null) {
            Log.e(TAG, "GATT Server is null, cannot add mesh service.")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot add mesh service.")
            return
        }

        val service = BluetoothGattService(MESH_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        
        // 메쉬 메시지 특성
        meshCharacteristic = BluetoothGattCharacteristic(
            MESH_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
        )
        
        // CCC 디스크립터 추가
        val cccDescriptor = BluetoothGattDescriptor(
            CLIENT_CONFIG_DESCRIPTOR_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        meshCharacteristic?.addDescriptor(cccDescriptor)
        
        service.addCharacteristic(meshCharacteristic)
        
        try {
            gattServer?.addService(service)
            Log.i(TAG, "Mesh service added successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add mesh service", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun closeGattServer() {
        if (!isServerOpen || gattServer == null) return
         if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            try {
                gattServer?.close()
                Log.i(TAG, "GATT Server closed.")
            } catch (e: Exception) {
                 Log.e(TAG, "Error closing GATT server", e)
            }
         } else {
              Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot close server.")
         }
        gattServer = null
        isServerOpen = false
        connectedClients.clear()
    }

    private val gattServerCallback = object : BluetoothGattServerCallback(){
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int){
            Log.d(TAG, "onConnectionStateChange: Device ${device.address}, Status $status, NewState $newState")
            onConnectionStateChange(device, status, newState)

            if(newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Device connected: ${device.address}")
                connectedClients[device.address] = device
            } else if(newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Device disconnected: ${device.address}")
                connectedClients.remove(device.address)
                onClientUnsubscribed(device)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            if (descriptor.uuid == CLIENT_CONFIG_DESCRIPTOR_UUID) {
                val status = if (value != null && value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    Log.i(TAG, "Client ${device.address} subscribed to notifications")
                    onClientSubscribed(device)
                    BluetoothGatt.GATT_SUCCESS
                } else if (value != null && value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                     Log.i(TAG, "Client ${device.address} unsubscribed from notifications")
                     onClientUnsubscribed(device)
                     BluetoothGatt.GATT_SUCCESS
                } else {
                    Log.w(TAG, "Invalid descriptor write request from ${device.address}")
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
                }

                if (responseNeeded) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        gattServer?.sendResponse(device, requestId, status, offset, value)
                    } else {
                         Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT for sendResponse")
                    }
                }
            } else {
                Log.w(TAG, "Unknown descriptor write request UUID: ${descriptor.uuid}")
                if (responseNeeded) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, value)
                    } else {
                         Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT for sendResponse")
                    }
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic?) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot respond to read request.")
                return
            }
            
            if (device == null || characteristic == null) return
            
            // 요청된 특성 UUID에 따라 응답 처리
            val responseData = when (characteristic.uuid) {
                CHAT_CHARACTERISTIC_UUID -> chatCharacteristic?.value
                MESH_CHARACTERISTIC_UUID -> meshCharacteristic?.value
                else -> null
            }
            
            val status = if (responseData != null) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE
            gattServer?.sendResponse(device, requestId, status, offset, responseData)
        }
        
        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(device: BluetoothDevice?, requestId: Int, characteristic: BluetoothGattCharacteristic?, 
                                                preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot respond to write request.")
                return
            }
            
            if (device == null || characteristic == null || value == null) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                }
                return
            }
            
            val success = when (characteristic.uuid) {
                CHAT_CHARACTERISTIC_UUID -> {
                    // 일반 채팅 메시지 처리
                    val message = String(value, Charsets.UTF_8)
                    Log.d(TAG, "Received chat message: $message from ${device.address}")
                    characteristic.value = value
                    BluetoothGatt.GATT_SUCCESS
                }
                MESH_CHARACTERISTIC_UUID -> {
                    // 메쉬 네트워크 메시지 처리
                    try {
                        val meshMessage = MeshMessage.fromBytes(value)
                        if (meshMessage != null) {
                            Log.d(TAG, "Received mesh message: sender=${meshMessage.sender}, seq=${meshMessage.sequenceNumber}")
                            characteristic.value = value
                            onMeshMessageReceived(meshMessage)
                            BluetoothGatt.GATT_SUCCESS
                        } else {
                            Log.e(TAG, "Failed to parse mesh message")
                            BluetoothGatt.GATT_FAILURE
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing mesh message", e)
                        BluetoothGatt.GATT_FAILURE
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown characteristic write request: ${characteristic.uuid}")
                    BluetoothGatt.GATT_FAILURE
                }
            }
            
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, success, offset, value)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun broadcastMessage(message: String) {
        if (!isServerOpen || gattServer == null || chatCharacteristic == null) {
             Log.w(TAG, "Server not ready or characteristic not set.")
             return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot broadcast message.")
            return
        }

        val messageBytes = message.toByteArray(Charsets.UTF_8)
        chatCharacteristic?.value = messageBytes
        val clients = connectedClients.values

        Log.d(TAG, "Broadcasting chat message '$message' to ${clients.size} clients.")
        for (device in clients) {
            try {
                val success = gattServer?.notifyCharacteristicChanged(device, chatCharacteristic!!, false)
                 Log.d(TAG, "Notifying ${device.address}: ${if(success == true) "Success" else "Fail"}")
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying client ${device.address}", e)
            }
        }
    }
    
    /**
     * 메쉬 메시지 브로드캐스트
     * @param message 전송할 메시지
     */
    @SuppressLint("MissingPermission")
    fun broadcastMeshMessage(message: MeshMessage) {
        if (!isServerOpen || gattServer == null || meshCharacteristic == null) {
            Log.w(TAG, "Server not ready or mesh characteristic not set.")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot broadcast mesh message.")
            return
        }
        
        try {
            // 메시지를 바이트 배열로 직렬화
            val messageBytes = message.toBytes()
            meshCharacteristic?.value = messageBytes
            
            val clients = connectedClients.values
            Log.d(TAG, "Broadcasting mesh message to ${clients.size} clients: sender=${message.sender}, seq=${message.sequenceNumber}")
            
            for (device in clients) {
                try {
                    val success = gattServer?.notifyCharacteristicChanged(device, meshCharacteristic!!, false)
                    Log.d(TAG, "Mesh notify to ${device.address}: ${if(success == true) "Success" else "Fail"}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying mesh message to client ${device.address}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting mesh message", e)
        }
    }
    
    /**
     * 메쉬 메시지를 특정 기기에 전송
     * @param device 수신 기기
     * @param message 전송할 메시지
     * @return 전송 성공 여부
     */
    @SuppressLint("MissingPermission")
    fun sendMeshMessage(device: BluetoothDevice, message: MeshMessage): Boolean {
        if (!isServerOpen || gattServer == null || meshCharacteristic == null) {
            Log.w(TAG, "Server not ready or mesh characteristic not set.")
            return false
        }
        if (!connectedClients.containsKey(device.address)) {
            Log.w(TAG, "Device not connected: ${device.address}")
            return false
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot send mesh message.")
            return false
        }
        
        try {
            // 메시지를 바이트 배열로 직렬화
            val messageBytes = message.toBytes()
            meshCharacteristic?.value = messageBytes
            
            val success = gattServer?.notifyCharacteristicChanged(device, meshCharacteristic!!, false) ?: false
            Log.d(TAG, "Sending mesh message to ${device.address}: ${if(success) "Success" else "Fail"}")
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Error sending mesh message", e)
            return false
        }
    }
}