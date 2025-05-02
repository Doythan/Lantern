package com.ssafy.lantern.data.source.ble.gatt

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.widget.TextView
import com.ssafy.lantern.R
import java.util.UUID

class GattClientManager(private val activity: Activity) {

    // GattServer에 요청하기 위한 객체
    private lateinit var bluetoothGatt: BluetoothGatt
    private val connectedAddress = mutableSetOf<String>()

    // GATT SERVER의 BluetoothDevice에 연결함
    fun connectToDevice(device: BluetoothDevice){
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED) {

            if(connectedAddress.contains(device.address)){
                Log.d("BLE", "이미 연결된 기기입니다: ${device.address}")
                return
            }
            connectedAddress.add(device.address)
            bluetoothGatt = device.connectGatt(activity, false, gattCallback)
        } else {
            Log.e("BLE", "권한 없음: BLUETOOTH_CONNECT")
        }
    }

    // GATT Connect 이벤트 때 콜백함
    private val gattCallback = object : BluetoothGattCallback(){
        // 연결 상태가 변경 됐을 때 호출
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            val textView = activity.findViewById<TextView>(R.id.gattStatusTextView)
            // GATT 연결이 성공했을 때
            if(newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("GATT Connect", "1")
                if (ContextCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                    == PackageManager.PERMISSION_GRANTED
                ) gatt?.discoverServices() // Server에 Service 요청

                // 연결 확인
                activity.runOnUiThread {
                    textView.text = "${gatt?.device?.address}"
                }
            } // GATT 연결이 실패 했을 때
            else if(newState == BluetoothProfile.STATE_DISCONNECTED){


            }
        }

        // Server가 응답한다면 이 함수가 호출 됨
        // gatt 연결 객체 characteristic 같은게 있음 암튼
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int){
            if (status == BluetoothGatt.GATT_SUCCESS  &&
                ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {

                // Server에 Service 요청하면 gatt 객체에 service에 들어가 있음
                // 그래서 UUID로 찾는거임
                val characteristic = gatt
                    .getService(SERVICE_UUID)
                    ?.getCharacteristic(CHARACTERISTIC_UUID)

                if (characteristic != null) {
                    // 3. 알림(Notify) 수신 설정
                    gatt.setCharacteristicNotification(characteristic, true)

                    // 4. 알림 활성화 위한 Descriptor 쓰기
                    val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor) // 알림 활성화
                }
            }
        }

        // Charateristic이 바뀐다면
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ){
            val data = characteristic.value
            val message = String(data)
            val chatLogTextView = activity.findViewById<TextView>(R.id.chatLogTextView)
            activity.runOnUiThread {
                chatLogTextView.append("\n상대: $message")
            }
        }



    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }


}