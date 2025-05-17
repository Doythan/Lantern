package com.ssafy.lanterns.ui.view.main

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ssafy.lanterns.service.WakeWordService
import com.ssafy.lanterns.ui.screens.App
import com.ssafy.lanterns.ui.theme.LanternTheme
import com.ssafy.lanterns.ui.screens.main.MainViewModel
import com.ssafy.lanterns.utils.WakeWordUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /* -------------------------------------------------- *
     * ViewModels
     * -------------------------------------------------- */
    private val mainViewModel: MainViewModel by viewModels()

    private lateinit var requestOverlayPermissionLauncher: ActivityResultLauncher<Intent>

    // 일반 권한(RECORD_AUDIO, POST_NOTIFICATIONS)
    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allRequiredGranted = true

            // RECORD_AUDIO
            permissions[Manifest.permission.RECORD_AUDIO]?.let { granted ->
                if (granted) {
                    Log.d("MainActivity", "RECORD_AUDIO 권한 허용")
                } else {
                    Log.w("MainActivity", "RECORD_AUDIO 권한 거부")
                    Toast.makeText(this,
                        "마이크 권한이 거부되어 웨이크워드 기능을 사용할 수 없습니다.",
                        Toast.LENGTH_LONG
                    ).show()
                    allRequiredGranted = false
                }
            }

            // POST_NOTIFICATIONS (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions[Manifest.permission.POST_NOTIFICATIONS]?.let { granted ->
                    if (!granted) {
                        Log.w("MainActivity", "POST_NOTIFICATIONS 권한 거부")
                    }
                }
            }

            if (allRequiredGranted) checkAndRequestOverlayPermission()
        }

    /* -------------------------------------------------- *
     * 웨이크워드 브로드캐스트 수신
     * -------------------------------------------------- */
    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WakeWordService.ACTION_ACTIVATE_AI) {
                Log.d("MainActivity", "ACTION_ACTIVATE_AI 수신 → activateAI()")
                mainViewModel.activateAI()
            }
        }
    }

    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allRequiredPermissionsGranted = true

            permissions[Manifest.permission.RECORD_AUDIO]?.let { isGranted ->
                if (isGranted) {
                    Log.d("MainActivity", "RECORD_AUDIO 권한이 허용되었습니다.")
                } else {
                    Log.w("MainActivity", "RECORD_AUDIO 권한이 거부되었습니다.")
                    Toast.makeText(this, "마이크 권한이 거부되어 웨이크워드 기능을 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
                    allRequiredPermissionsGranted = false
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions[Manifest.permission.POST_NOTIFICATIONS]?.let { isGranted ->
                    if (isGranted) {
                        Log.d("MainActivity", "POST_NOTIFICATIONS 권한이 허용되었습니다.")
                    } else {
                        Log.w("MainActivity", "POST_NOTIFICATIONS 권한이 거부되었습니다.")
                    }
                }
            }

            if (allRequiredPermissionsGranted) {
                checkAndRequestOverlayPermission()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Porcupine 모델 파일 미발견 시 WakeWord 기능 완전 비활성화 ──
        if (!WakeWordUtils.hasModelFiles(this)) {
            Log.w("MainActivity", "모델 파일(.pv/.ppn) 미발견 → WakeWord 기능 비활성화")
            setContent {
                LanternTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        App()  // UI만 띄우고 서비스·권한 요청 없이 종료
                    }
                }
            }
            return
        }

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        requestOverlayPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (Settings.canDrawOverlays(this)) {
                    Log.d("MainActivity", "다른 앱 위에 그리기 권한 허용됨 (설정 후)")
                    startWakeWordService()
                } else {
                    Log.w("MainActivity", "다른 앱 위에 그리기 권한 거부됨 (설정 후)")
                    Toast.makeText(this, "다른 앱 위에 표시 권한이 거부되어 AI 화면을 표시할 수 없습니다.", Toast.LENGTH_LONG).show()
                }
            }

        /* --- Compose UI --- */
        setContent {
            LanternTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App()
                }
            }
        }

        checkAndRequestPermissions()

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(wakeWordReceiver, IntentFilter(WakeWordService.ACTION_ACTIVATE_AI))
        Log.d("MainActivity", "wakeWordReceiver 등록됨.")
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(wakeWordReceiver)
        Log.d("MainActivity", "wakeWordReceiver 해제")
    }

    /* ==================================================
     *  권한 / Overlay 처리
     * ================================================== */
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            Log.d("MainActivity", "요청할 권한: $permissions")
            requestMultiplePermissionsLauncher.launch(permissions.toTypedArray())
        } else {
            Log.d("MainActivity", "일반 권한 이미 모두 허용됨")
            checkAndRequestOverlayPermission()
        }
    }

    private fun checkAndRequestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Log.d("MainActivity", "다른 앱 위에 그리기 권한 없음 → 설정 화면 이동")
            Toast.makeText(this, "AI 화면을 표시하려면 '다른 앱 위에 표시' 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            requestOverlayPermissionLauncher.launch(intent)
        } else {
            Log.d("MainActivity", "다른 앱 위에 그리기 권한 이미 허용됨")
            startWakeWordService()
        }
    }

    private fun startWakeWordService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            Settings.canDrawOverlays(this)
        ) {
            Log.d("MainActivity", "필수 권한 모두 허용→WakeWordService 시작")
            val intent = Intent(this, WakeWordService::class.java)
            ContextCompat.startForegroundService(this, intent)
        } else {
            Log.w("MainActivity", "서비스 시작에 필요한 권한 부족")
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "마이크 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "'다른 앱 위에 표시' 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(wakeWordReceiver)
        Log.d("MainActivity", "wakeWordReceiver 등록 해제됨.")
    }
}
