package com.ssafy.lantern.utils

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private const val TAG = "PermissionHelper"

class PermissionHelper(private val activity: Activity) {
    private val perms = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.RECORD_AUDIO
    )

    fun isBluetoothEnabled(): Boolean {
        val mgr = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return mgr?.adapter?.isEnabled == true
    }

    fun requestEnableBluetooth(launcher: ActivityResultLauncher<Intent>) {
        if (!isBluetoothEnabled()) {
            try {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                launcher.launch(enableBtIntent)
            } catch (e: Exception) {
                Log.e(TAG, "블루투스 활성화 요청 실패: ${e.message}")
            }
        }
    }

    fun hasPermission(): Boolean {
        val missingPermissions = perms.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            Log.d(TAG, "누락된 권한: ${missingPermissions.joinToString()}")
            return false
        }

        return true
    }

    fun requestPermissions(requestCode: Int) {
        val missingPermissions = perms.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, missingPermissions, requestCode)
            Log.d(TAG, "요청한 권한: ${missingPermissions.joinToString()}")
        }
    }

    // 특정 권한만 확인
    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    // 권한 거부 횟수를 추적하고 권한이 필요한 이유 설명 대화상자를 표시할 때 유용
    fun shouldShowRationale(permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    // 권한 요청을 위한 메서드(ActivityResultLauncher 사용)
    fun requestPermissions(launcher: ActivityResultLauncher<Array<String>>) {
        val missingPermissions = perms.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            launcher.launch(missingPermissions)
            Log.d(TAG, "요청한 권한(런처): ${missingPermissions.joinToString()}")
        }
    }
}