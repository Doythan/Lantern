package com.ssafy.lanterns.ui.view.main

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri // Uri import 추가
import android.os.Build
import android.os.Bundle
import android.provider.Settings // Settings import 추가
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher // ActivityResultLauncher import 추가
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ssafy.lanterns.service.WakeWordService
import com.ssafy.lanterns.ui.navigation.AppNavigation // App() 대신 AppNavigation()을 직접 사용한다고 가정
import com.ssafy.lanterns.ui.screens.main.MainViewModel
import com.ssafy.lanterns.ui.theme.LanternTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    // 다른 앱 위에 그리기 권한 결과를 처리하기 위한 ActivityResultLauncher
    private lateinit var requestOverlayPermissionLauncher: ActivityResultLauncher<Intent>

    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WakeWordService.ACTION_ACTIVATE_AI) {
                Log.d("MainActivity", "로컬 브로드캐스트 수신: ACTION_ACTIVATE_AI. mainViewModel.activateAI() 호출.")
                mainViewModel.activateAI()
            }
        }
    }

    // 일반 권한 요청 런처 (RECORD_AUDIO, POST_NOTIFICATIONS 등)
    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allRequiredPermissionsGranted = true

            // RECORD_AUDIO 권한 결과 처리
            permissions[Manifest.permission.RECORD_AUDIO]?.let { isGranted ->
                if (isGranted) {
                    Log.d("MainActivity", "RECORD_AUDIO 권한이 허용되었습니다.")
                } else {
                    Log.w("MainActivity", "RECORD_AUDIO 권한이 거부되었습니다.")
                    Toast.makeText(this, "마이크 권한이 거부되어 웨이크워드 기능을 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
                    allRequiredPermissionsGranted = false
                }
            }

            // POST_NOTIFICATIONS 권한 결과 처리 (Android 13 이상)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions[Manifest.permission.POST_NOTIFICATIONS]?.let { isGranted ->
                    if (isGranted) {
                        Log.d("MainActivity", "POST_NOTIFICATIONS 권한이 허용되었습니다.")
                    } else {
                        Log.w("MainActivity", "POST_NOTIFICATIONS 권한이 거부되었습니다.")
                        // 알림 권한은 부가 기능이므로 allRequiredPermissionsGranted에 영향을 주지 않을 수 있음
                        // Toast.makeText(this, "알림 권한이 거부되어 서비스 알림이 표시되지 않을 수 있습니다.", Toast.LENGTH_LONG).show()
                    }
                }
            }

            // 모든 필수 권한이 허용되었는지 확인 후 서비스 시작
            if (allRequiredPermissionsGranted) {
                // RECORD_AUDIO 권한이 허용되었으므로, 이제 다른 앱 위에 그리기 권한 확인
                checkAndRequestOverlayPermission()
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 다른 앱 위에 그리기 권한 요청 결과 처리 런처 초기화
        requestOverlayPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                // 사용자가 설정 화면에서 돌아왔을 때 다시 권한 확인
                if (Settings.canDrawOverlays(this)) {
                    Log.d("MainActivity", "다른 앱 위에 그리기 권한이 허용되었습니다. (설정 후)")
                    startWakeWordService()
                } else {
                    Log.w("MainActivity", "다른 앱 위에 그리기 권한이 거부되었습니다. (설정 후)")
                    Toast.makeText(this, "다른 앱 위에 표시 권한이 거부되어 AI 화면을 표시할 수 없습니다.", Toast.LENGTH_LONG).show()
                }
            }

        setContent {
            LanternTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // App() 또는 AppNavigation() 호출
                    // 이전 질문에서 App.kt의 AppContent를 사용한다고 가정했으므로, 그에 맞게 App() 호출
                    // 만약 AppNavigation()을 직접 호출한다면 해당 부분으로 변경
                    com.ssafy.lanterns.ui.screens.App() // App.kt의 App() Composable 호출
                }
            }
        }

        checkAndRequestPermissions() // 일반 권한 먼저 요청
        LocalBroadcastManager.getInstance(this).registerReceiver(
            wakeWordReceiver, IntentFilter(WakeWordService.ACTION_ACTIVATE_AI)
        )
        Log.d("MainActivity", "wakeWordReceiver 등록됨.")
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
        }

        // 2. POST_NOTIFICATIONS 권한 확인 (Android 13 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d("MainActivity", "요청할 권한: $permissionsToRequest")
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.d("MainActivity", "일반 권한이 이미 모두 허용되어 있습니다.")
            // 일반 권한이 모두 허용된 경우, 다른 앱 위에 그리기 권한 확인
            checkAndRequestOverlayPermission()
        }
    }

    // 다른 앱 위에 그리기 권한 확인 및 요청 함수
    private fun checkAndRequestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Log.d("MainActivity", "다른 앱 위에 그리기 권한이 없습니다. 설정 화면으로 이동합니다.")
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            // 설정 화면으로 이동하여 사용자가 직접 권한을 설정하도록 함
            Toast.makeText(this, "AI 화면을 표시하려면 '다른 앱 위에 표시' 권한이 필요합니다. 설정에서 Lantern 앱을 찾아 권한을 허용해주세요.", Toast.LENGTH_LONG).show()
            requestOverlayPermissionLauncher.launch(intent)
        } else {
            Log.d("MainActivity", "다른 앱 위에 그리기 권한이 이미 허용되어 있습니다.")
            // 모든 필수 권한이 준비되었으므로 서비스 시작
            startWakeWordService()
        }
    }

    private fun startWakeWordService() {
        // RECORD_AUDIO와 SYSTEM_ALERT_WINDOW 권한이 모두 있어야 서비스가 정상 동작 가능
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            Settings.canDrawOverlays(this)
        ) {
            Log.d("MainActivity", "모든 필수 권한이 허용되어 WakeWordService를 시작합니다.")
            val intent = Intent(this, WakeWordService::class.java)
            ContextCompat.startForegroundService(this, intent)
        } else {
            Log.w("MainActivity", "WakeWordService 시작에 필요한 권한이 부족합니다. (RECORD_AUDIO: ${ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)}, SYSTEM_ALERT_WINDOW: ${Settings.canDrawOverlays(this)})")
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "마이크 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "'다른 앱 위에 표시' 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(wakeWordReceiver)
        Log.d("MainActivity", "wakeWordReceiver 등록 해제됨.")
    }
}