package com.example.blemodule.ui.view

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.blemodule.databinding.ActivityChatBinding
import com.example.blemodule.service.BleService
import com.example.blemodule.ui.view.components.ChatAdapter
import com.example.blemodule.ui.viewmodel.ChatViewModel
import com.example.blemodule.util.PermissionHelper

class ChatActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "ChatActivity"
    }

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var bleService: BleService
    private lateinit var chatAdapter: ChatAdapter

    // 요청할 BLE 권한 목록
    private val REQUIRED_PERMS = arrayOf(
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )
    private val REQUEST_CODE_PERM = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // RecyclerView + Adapter 설정
        chatAdapter = ChatAdapter()
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity)
            adapter = chatAdapter
        }

        // ViewModel 메시지 관찰 → 화면에 뿌리기
        viewModel.messages.observe(this) { list ->
            chatAdapter.submitList(list)
            binding.recyclerView.scrollToPosition(list.size - 1)
        }

        // BLE 권한 확인 후 서비스 초기화
        val myId = intent.getStringExtra("MY_DEVICE_ID") ?: ""
        if (PermissionHelper.hasPermissions(this, *REQUIRED_PERMS)) {
            initBleService(myId)
        } else {
            PermissionHelper.requestPermissions(this, REQUIRED_PERMS, REQUEST_CODE_PERM)
        }

        // 메시지 전송 버튼 리스너
        binding.btnSend.setOnClickListener {
            val targetId = binding.etTargetId.text.toString().trim().uppercase()
            val payload = binding.etMessage.text.toString().trim()
            if (targetId.isNotEmpty() && payload.isNotEmpty()) {
                // 로컬에 내 메시지 추가
                viewModel.sendAppMessage(targetId, payload)
                // BLE 네트워크로 전송
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    bleService.sendAppMessage(targetId, payload)
                }
                binding.etMessage.text?.clear()
            }
        }
    }

    // 권한 요청 결과 처리
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERM &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            val myId = intent.getStringExtra("MY_DEVICE_ID") ?: ""
            initBleService(myId)
        }
    }

    @SuppressLint("MissingPermission")
    private fun initBleService(deviceId: String) {
        // BLUETOOTH_CONNECT 권한 확인
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "initBleService: BLUETOOTH_CONNECT 권한 없음")
            return
        }
        bleService = BleService(applicationContext).apply {
            setAppMessageListener { sourceId, msg ->
                viewModel.addReceivedMessage(sourceId, msg)
            }
            startService(deviceId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // BLE 서비스 중지
        if (::bleService.isInitialized) bleService.stopService()
    }
}
