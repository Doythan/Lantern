package com.ssafy.lanterns.ui.view.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.Surface
import androidx.core.content.ContextCompat
import com.ssafy.lanterns.ui.navigation.AppNavigation
import com.ssafy.lanterns.ui.theme.LanternTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // 블루투스 권한 요청 결과 처리
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "블루투스 기능을 사용하려면 권한이 필요합니다", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 블루투스 권한 확인 및 요청
        checkAndRequestBluetoothPermissions()

        setContent {
            LanternTheme {
                Surface {
                    val navController = androidx.navigation.compose.rememberNavController()
                    AppNavigation(navController = navController)
                }
            }
        }
    }
    
    // 블루투스 권한 확인 및 요청
    private fun checkAndRequestBluetoothPermissions() {
        // Android 12 (API 31) 이상에서 필요한 권한
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            // Android 12 미만에서 필요한 권한
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        
        // 권한이 모두 있는지 확인
        val allPermissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        
        // 권한이 없으면 요청
        if (!allPermissionsGranted) {
            permissionLauncher.launch(requiredPermissions)
        }
    }
}