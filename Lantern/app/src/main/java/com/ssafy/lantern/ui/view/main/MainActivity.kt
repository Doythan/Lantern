package com.ssafy.lantern.ui.view.main

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ssafy.lantern.MyApp
import com.ssafy.lantern.R
import com.ssafy.lantern.data.source.ble.advertiser.AdvertiserManager
import com.ssafy.lantern.data.source.ble.gatt.GattServerManager
import com.ssafy.lantern.data.source.ble.scanner.ScannerManager
import com.ssafy.lantern.utils.PermissionHelper

class MainActivity : AppCompatActivity() {

    private lateinit var permissionHelper : PermissionHelper

    private lateinit var advertiserManager : AdvertiserManager

    private lateinit var scannerManager : ScannerManager

    private lateinit var gattServerManager : GattServerManager


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val db = (application as MyApp).db
        db.openHelper.readableDatabase


        // PermissionHelper 객체 생성
        permissionHelper = PermissionHelper(this)

        // AdvertiserManager 객체 생성
        advertiserManager = AdvertiserManager(this)

        val deviceInfoTextView = findViewById<TextView>(R.id.deviceInfoTextView)


        // ScannerManager 객체 생성
        scannerManager = ScannerManager(this, deviceInfoTextView)

        // GattServerManager 객체 생성
        gattServerManager = GattServerManager(this)

        // 권한이 없다면 요청
        if(!permissionHelper.hasPermission()) permissionHelper.requestPermissions(1001);
        // 있다면
        else{
            // 블루투스를 사용자가 켰는지 확인
            if(permissionHelper.isBluetoothEnabeld()) {
                // gatt server 열기
                gattServerManager.openGattServer()
                // advertising
                advertiserManager.startAdvertising()
                // scaning
                scannerManager.startScanning()
                Log.d("dong", "ㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇ")
            }
            else Log.d("1234", "연결 되지 않았습니다.")
        }

        // ui component와 바인딩
        val messageInput = findViewById<EditText>(R.id.messageInput)
        val sendButton = findViewById<Button>(R.id.sendButton)
        val chatLogTextView = findViewById<TextView>(R.id.chatLogTextView)

        // button 이벤트
        sendButton.setOnClickListener {
            val message = messageInput.text.toString()
            if (message.isNotBlank()) {
                gattServerManager.broadcastMessage(message)
                chatLogTextView.append("\n나: $message")
                messageInput.setText("")
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun showToast(message: String){
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}