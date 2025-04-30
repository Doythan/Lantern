package com.example.blemodule.util

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 블루투스 관련 유틸리티 함수들을 모아놓은 객체입니다.
 */
object BluetoothUtils {

    /**
     * 시스템의 BluetoothAdapter 인스턴스를 가져옵니다.
     * @param context 애플리케이션 컨텍스트
     * @return BluetoothAdapter 인스턴스 또는 null (어댑터 사용 불가 시)
     */
    fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        // BluetoothManager 시스템 서비스 가져오기
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        // BluetoothManager 를 통해 BluetoothAdapter 반환
        return bluetoothManager?.adapter
    }

    /**
     * 기기가 BLE를 지원하는지 확인합니다.
     * @param context 애플리케이션 컨텍스트
     * @return BLE 지원 시 true, 미지원 시 false
     */
    fun isBleSupported(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    /**
     * 블루투스가 현재 활성화되어 있는지 확인합니다.
     * @param context 애플리케이션 컨텍스트
     * @return 활성화 시 true, 비활성화 또는 사용 불가 시 false
     * @throws SecurityException BLUETOOTH_CONNECT 권한이 없을 경우 (API 31+)
     */
    @Throws(SecurityException::class)
    fun isBluetoothEnabled(context: Context): Boolean {
        val adapter = getBluetoothAdapter(context)
        // 어댑터가 null 이 아니고, 활성화 상태인지 확인
        // isEnabled 접근 시 API 31+ 에서는 BLUETOOTH_CONNECT 권한 필요
        return adapter != null && adapter.isEnabled
    }

    /**
     * BLE 관련 필수 권한들이 모두 허용되었는지 확인합니다.
     * Android 버전에 따라 필요한 권한 목록이 달라집니다.
     * @param context 애플리케이션 컨텍스트
     * @return 모든 필수 권한이 허용되었으면 true, 아니면 false
     */
    fun hasRequiredPermissions(context: Context): Boolean {
        // Android 12 (API 31) 이상일 경우 필요한 권한 목록
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED // 위치 권한도 여전히 필요할 수 있음
        }
        // Android 10, 11 (API 29, 30) 경우
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED // 백그라운드 스캔 시 필요
            // BLUETOOTH, BLUETOOTH_ADMIN은 manifest 선언으로 충분
        }
        // Android 9 (API 28) 이하 경우
        else {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            // BLUETOOTH, BLUETOOTH_ADMIN은 manifest 선언으로 충분
        }
    }

    /**
     * 앱이 백그라운드에서 위치 정보에 접근할 수 있는 권한이 있는지 확인합니다. (Android 10 이상)
     * @param context 애플리케이션 컨텍스트
     * @return 백그라운드 위치 권한이 있으면 true, 없으면 false
     */
    fun hasBackgroundLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 10 미만에서는 별도 백그라운드 위치 권한 없음
            true
        }
    }

    /**
     * 알림 표시 권한이 있는지 확인합니다. (Android 13 이상)
     * @param context 애플리케이션 컨텍스트
     * @return 알림 권한이 있으면 true, 없으면 false
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 13 미만에서는 별도 알림 권한 없음
            true
        }
    }

}