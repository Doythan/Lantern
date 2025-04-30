package com.example.blemodule.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 런타임 권한 요청 관련 로직을 도와주는 객체입니다.
 */
object PermissionHelper {

    // 요청할 BLE 관련 권한 목록 정의 (Android 버전에 따라 동적으로 결정)
    val blePermissions: Array<String> by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12 (API 31) 이상
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION // 스캔 결과에서 위치 정보 얻으려면 여전히 필요
                // 필요 시 ACCESS_COARSE_LOCATION 추가
            )
        } else { // Android 11 (API 30) 이하
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION // 보통 Fine 만 요청해도 됨
                // BLUETOOTH, BLUETOOTH_ADMIN 은 설치 시 권한
            )
        }
    }

    // 백그라운드 위치 권한 (Android 10 이상)
    val backgroundLocationPermission: String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Manifest.permission.ACCESS_BACKGROUND_LOCATION else null

    // 알림 권한 (Android 13 이상)
    val notificationPermission: String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null


    /**
     * 주어진 권한들이 모두 허용되었는지 확인합니다.
     * @param context 컨텍스트
     * @param permissions 확인할 권한 목록
     * @return 모든 권한이 허용되었으면 true, 아니면 false
     */
    fun arePermissionsGranted(context: Context, permissions: Array<String>): Boolean {
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * BLE 관련 필수 권한이 모두 허용되었는지 확인합니다.
     * @param context 컨텍스트
     * @return 모든 권한이 허용되었으면 true, 아니면 false
     */
    fun hasBlePermissions(context: Context): Boolean {
        return arePermissionsGranted(context, blePermissions)
    }

    /**
     * 백그라운드 위치 권한이 허용되었는지 확인합니다.
     * @param context 컨텍스트
     * @return 권한이 허용되었으면 true, 아니면 false (Android 10 미만 포함)
     */
    fun hasBackgroundLocationPermission(context: Context): Boolean {
        return backgroundLocationPermission?.let {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        } ?: true // Android 10 미만이면 항상 true
    }

    /**
     * 알림 권한이 허용되었는지 확인합니다.
     * @param context 컨텍스트
     * @return 권한이 허용되었으면 true, 아니면 false (Android 13 미만 포함)
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return notificationPermission?.let {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        } ?: true // Android 13 미만이면 항상 true
    }


    /**
     * 사용자에게 권한 요청을 보냅니다. (Activity 에서 호출)
     * registerForActivityResult 와 함께 사용해야 합니다.
     * @param launcher 권한 요청 결과를 처리할 ActivityResultLauncher
     * @param permissions 요청할 권한 목록
     */
    fun requestPermissions(launcher: ActivityResultLauncher<Array<String>>, permissions: Array<String>) {
        launcher.launch(permissions)
    }

    /**
     * BLE 관련 필수 권한을 요청합니다.
     * @param launcher 권한 요청 결과를 처리할 ActivityResultLauncher
     */
    fun requestBlePermissions(launcher: ActivityResultLauncher<Array<String>>) {
        requestPermissions(launcher, blePermissions)
    }

    /**
     * 백그라운드 위치 권한을 요청합니다. (이미 Fine Location 권한이 허용된 상태에서 요청해야 함)
     * @param launcher 권한 요청 결과를 처리할 ActivityResultLauncher
     */
    fun requestBackgroundLocationPermission(launcher: ActivityResultLauncher<String>) {
        backgroundLocationPermission?.let { launcher.launch(it) }
    }

    /**
     * 알림 권한을 요청합니다.
     * @param launcher 권한 요청 결과를 처리할 ActivityResultLauncher
     */
    fun requestNotificationPermission(launcher: ActivityResultLauncher<String>) {
        notificationPermission?.let { launcher.launch(it) }
    }


    /**
     * 사용자가 권한 요청을 거부했는지, "다시 묻지 않음"을 선택했는지 확인합니다. (Activity 에서 사용)
     * @param activity 컨텍스트로 사용할 Activity
     * @param permission 확인할 권한
     * @return 사용자가 명시적으로 거부했거나 다시 묻지 않음을 선택했다면 true
     */
    fun shouldShowRationale(activity: Activity, permission: String): Boolean {
        // ActivityCompat.shouldShowRequestPermissionRationale 는 사용자가 처음 거부했을 때 true 반환
        // 권한이 없고, Rationale 표시 필요성도 없으면 -> "다시 묻지 않음" 상태로 간주 가능
        return ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
}