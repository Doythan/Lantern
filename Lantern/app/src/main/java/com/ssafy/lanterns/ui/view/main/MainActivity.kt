package com.ssafy.lanterns.ui.view.main

import android.Manifest // Manifest import
import android.content.Intent
import android.content.pm.PackageManager // PackageManager import
import android.os.Build // Build import
import android.os.Bundle
import android.util.Log // Log import
import android.widget.Toast // Toast import
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts // ActivityResultContracts import
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat // ContextCompat import
import androidx.core.view.WindowCompat
import com.ssafy.lanterns.service.WakeWordService
import com.ssafy.lanterns.ui.navigation.AppNavigation
import com.ssafy.lanterns.ui.theme.LanternTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // 여러 권한을 한 번에 요청하고 결과를 처리하는 런처
    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // RECORD_AUDIO 권한 결과 처리
            permissions[Manifest.permission.RECORD_AUDIO]?.let { isGranted ->
                if (isGranted) {
                    Log.d("MainActivity", "RECORD_AUDIO 권한이 허용되었습니다.")
                    startWakeWordService()
                } else {
                    Log.w("MainActivity", "RECORD_AUDIO 권한이 거부되었습니다.")
                    Toast.makeText(this, "마이크 권한이 거부되어 웨이크워드 기능을 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
                }
            }

            // POST_NOTIFICATIONS 권한 결과 처리 (Android 13 이상)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions[Manifest.permission.POST_NOTIFICATIONS]?.let { isGranted ->
                    if (isGranted) {
                        Log.d("MainActivity", "POST_NOTIFICATIONS 권한이 허용되었습니다.")
                    } else {
                        Log.w("MainActivity", "POST_NOTIFICATIONS 권한이 거부되었습니다.")
                        Toast.makeText(this, "알림 권한이 거부되어 서비스 알림이 표시되지 않을 수 있습니다.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 상태바, 내비게이션 바 투명하게 처리하고 전체화면 모드로 설정
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            LanternTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // App() 대신 직접 AppNavigation()을 호출하여 시작 화면을 표시합니다.
                    AppNavigation()
                }
            }
        }

        // 앱 시작 시 필요한 권한 확인 및 요청
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // 1. RECORD_AUDIO 권한 확인
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        } else {
            Log.d("MainActivity", "RECORD_AUDIO 권한이 이미 허용되어 있습니다.")
        }

        // 2. POST_NOTIFICATIONS 권한 확인 (Android 13 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Log.d("MainActivity", "POST_NOTIFICATIONS 권한이 이미 허용되어 있습니다.")
            }
        }

        // 요청할 권한이 있다면 요청 실행
        if (permissionsToRequest.isNotEmpty()) {
            Log.d("MainActivity", "요청할 권한: $permissionsToRequest")
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.d("MainActivity", "필요한 모든 권한이 이미 허용되어 있습니다.")
            startWakeWordService()

        }
    }

    private fun startWakeWordService() {
        val intent = Intent(this, WakeWordService::class.java)
        // API 26+에선 반드시 startForegroundService 사용
        ContextCompat.startForegroundService(this, intent)
    }
}