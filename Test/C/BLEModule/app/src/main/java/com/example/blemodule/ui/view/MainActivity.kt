package com.example.blemodule.ui.view

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.blemodule.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge 적용
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }

        // 채팅 화면으로 이동 버튼
        binding.btnGoChat.setOnClickListener {
            val id = binding.etDeviceId.text.toString().trim().uppercase()
            if (id.isEmpty() || id !in listOf("A","B","C")) {
                binding.etDeviceId.error = "유효한 ID(A, B, C)를 입력하세요"
            } else {
                val intent = Intent(this, ChatActivity::class.java).apply {
                    putExtra("MY_DEVICE_ID", id)
                }
                startActivity(intent)
            }
        }
    }
}
