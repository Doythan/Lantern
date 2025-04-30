package com.example.blemodule

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.blemodule.ble.BleManager
import com.example.blemodule.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var bleManager: BleManager
    
    private val bluetoothManager by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter by lazy {
        bluetoothManager.adapter
    }
    
    // 필요한 권한 목록
    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    
    // 권한 요청 결과 처리
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        
        if (allGranted) {
            Log.d(TAG, "모든 권한 허용됨")
            // 블루투스 활성화 확인
            checkBluetoothEnabled()
        } else {
            Log.e(TAG, "일부 권한 거부됨")
            Toast.makeText(
                this,
                "BLE 채팅을 위해서는 모든 권한이 필요합니다.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    // 블루투스 활성화 요청 결과 처리
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d(TAG, "블루투스 활성화됨")
            // BLE 서비스 시작
            startBleService()
        } else {
            Log.e(TAG, "블루투스 활성화 거부됨")
            Toast.makeText(
                this,
                "BLE 채팅을 위해서는 블루투스가 필요합니다.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // UI 초기화
        initializeUI()
        
        // 권한 확인
        checkPermissions()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // BLE 서비스 중지
        if (::bleManager.isInitialized) {
            bleManager.stopService()
        }
    }
    
    // UI 초기화
    private fun initializeUI() {
        // 전송 버튼 클릭 리스너
        binding.btnSend.setOnClickListener {
            val message = binding.etMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                // 메시지 전송
                if (::bleManager.isInitialized) {
                    bleManager.sendMessage(message)
                    binding.etMessage.text.clear()
                }
            }
        }
    }
    
    // 권한 확인
    private fun checkPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "권한 요청: ${permissionsToRequest.joinToString()}")
            requestPermissionLauncher.launch(permissionsToRequest)
        } else {
            Log.d(TAG, "이미 모든 권한 허용됨")
            // 블루투스 활성화 확인
            checkBluetoothEnabled()
        }
    }
    
    // 블루투스 활성화 확인
    private fun checkBluetoothEnabled() {
        if (bluetoothAdapter?.isEnabled == true) {
            Log.d(TAG, "블루투스 이미 활성화됨")
            // BLE 서비스 시작
            startBleService()
        } else {
            Log.d(TAG, "블루투스 활성화 요청")
            // 블루투스 활성화 요청
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        }
    }
    
    // BLE 서비스 시작
    private fun startBleService() {
        Log.d(TAG, "BLE 서비스 시작")
        
        // 기기 이름 설정
        val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter?.name ?: "Unknown Device"
            } else {
                "Unknown Device"
            }
        } else {
            bluetoothAdapter?.name ?: "Unknown Device"
        }
        
        binding.tvMyName.text = "내 이름: $deviceName"
        
        // BLE 매니저 초기화
        bleManager = BleManager(this, deviceName)
        
        // 메시지 수신 Flow 구독
        bleManager.messageFlow
            .onEach { message ->
                // 메시지 표시
                val formattedTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(Date(message.timestamp))
                
                val formattedMessage = when (message.messageType) {
                    com.example.blemodule.ble.MessageType.SYSTEM -> {
                        "[시스템] ${message.content}\n"
                    }
                    else -> {
                        "[${formattedTime}] ${message.senderName}: ${message.content}\n"
                    }
                }
                
                runOnUiThread {
                    binding.tvChatMessages.append(formattedMessage)
                    binding.scrollView.post {
                        binding.scrollView.fullScroll(android.widget.ScrollView.FOCUS_DOWN)
                    }
                }
            }
            .launchIn(lifecycleScope)
        
        // 연결된 기기 목록 Flow 구독
        bleManager.connectedDevicesFlow
            .onEach { deviceNames ->
                val devicesText = if (deviceNames.isEmpty()) {
                    "연결된 기기: 없음"
                } else {
                    "연결된 기기: ${deviceNames.joinToString(", ")}"
                }
                
                runOnUiThread {
                    binding.tvConnectedDevices.text = devicesText
                }
            }
            .launchIn(lifecycleScope)
        
        // BLE d서비스 시작
        bleManager.startService()
    }
}
