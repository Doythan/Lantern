package com.ssafy.lantern.data.source.ble.gatt

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import androidx.core.content.ContextCompat
import android.Manifest
import android.bluetooth.BluetoothGattService
import android.content.pm.PackageManager
import android.util.Log
import java.util.UUID

class GattServerManager (private val activity: Activity){
    private lateinit var gattServer : BluetoothGattServer
    private val connectedClients = mutableSetOf<BluetoothDevice>()
    private lateinit var characteristic: BluetoothGattCharacteristic

    fun openGattServer(){
        val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        // 권한 확인
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED) {

            // Gatt Server를 열어줌
            // 다른 device가 이걸로 연결
            gattServer = bluetoothManager.openGattServer(activity, gattServerCallback)

        } else {
            Log.e("오픈가트서버체크", "BLUETOOTH_CONNECT 권한이 없어 GATT 서버를 열 수 없습니다.")
            // 권한 요청 로직 추가 필요
        }

        // Service meta data
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        if (gattServer != null) {
            val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            characteristic = BluetoothGattCharacteristic(
                // characteristic UUID
                // character 를 구별하기 위함
                CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            service.addCharacteristic(characteristic)
            gattServer.addService(service)
        } else {
            Log.e("BLE", "GATT 서버 초기화 실패")
        }


    }

    private val gattServerCallback = object : BluetoothGattServerCallback(){
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int){
            if(newState == BluetoothProfile.STATE_CONNECTED)
            {

                connectedClients.add(device)
            }
            else if(newState == BluetoothProfile.STATE_DISCONNECTED)
                connectedClients.remove(device)
        }
    }

    fun broadcastMessage(message: String){
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED) {

            characteristic.value = message.toByteArray()
            for (device in connectedClients) {
                Log.d("message", "${device}")
                gattServer.notifyCharacteristicChanged(device, characteristic, false)
            }

        } else {
            Log.e("BLE", "권한 없음: BLUETOOTH_CONNECT")
            // 권한 요청 로직 또는 사용자 알림 추가 가능
        }
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")
    }

}