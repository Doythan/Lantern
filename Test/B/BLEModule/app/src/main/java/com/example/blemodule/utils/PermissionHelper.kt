package com.example.blemodule.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap

object PermissionHelper {
    // 권한 캐시 (성능 최적화)
    private val permissionCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()
    private const val CACHE_VALIDITY_MS = 5000 // 5초

    fun hasPermission(context: Context, permission: String): Boolean {
        val cachedResult = permissionCache[permission]
        val currentTime = System.currentTimeMillis()

        // 캐시가 유효하면 캐시된 결과 반환
        if (cachedResult != null && (currentTime - cachedResult.second) < CACHE_VALIDITY_MS) {
            return cachedResult.first
        }

        // 실제 권한 확인
        val result = ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED

        // 결과 캐싱
        permissionCache[permission] = Pair(result, currentTime)
        return result
    }

    fun hasBluetoothScanPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(context, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            // Android 12 미만에서는 위치 권한으로 대체
            hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun hasBluetoothAdvertisePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            true  // Android 12 미만에서는 별도 권한 불필요
        }
    }

    fun hasBluetoothConnectPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            true  // Android 12 미만에서는 별도 권한 불필요
        }
    }

    fun hasRequiredPermissions(context: Context): Boolean {
        // Android 12 (S) 이상
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return hasPermission(context, Manifest.permission.BLUETOOTH_SCAN) &&
                    hasPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) &&
                    hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
        }

        // Android 12 미만
        return hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    }
}