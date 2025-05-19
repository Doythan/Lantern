package com.ssafy.lanterns.utils
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// private val activity를 받아와야 어떤 화면에서 권한 요청 화면을 띄울지 알 수 있음
class PermissionHelper(private val activity: Activity) {
    // Bluetooth가 현재 활성화 되어있는지
    // Context 상수를 모아놓은 클래스라고 생각하면 됨
    fun isBluetoothEnabeld(): Boolean{
        // Context는 Application Context와 Activity Context가 두 가지 존재
        // Manager를 호출하는데 any type을 호출하기 때문에 형변환 해줘야됨
        val bluetoothAdapter = (activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        // null == true -> false
        return bluetoothAdapter?.isEnabled == true;
    }

    // bluetooth permission을 가지고 있는지
    fun hasPermission(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 (API 31) 이상
            // BLUETOOTH_SCAN에 neverForLocation 플래그가 있으므로, ACCESS_FINE_LOCATION은 필수가 아님.
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            // Android 11 (API 30) 이하
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION // 스캔에 필수
            )
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // bluetooth 권한 요청
    // 일단은 모든 요청을 하는 씩으로 개발하고 나중에 뭔가 터지면 그 때 바꾸자
    fun requestPermissions(requestCode: Int) {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mutableListOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
            // ACCESS_FINE_LOCATION 요청은 AndroidManifest.xml의 BLUETOOTH_SCAN 설정과 앱의 요구사항에 따라 결정
            // 현재 neverForLocation이므로, 여기서는 명시적으로 요청하지 않음.
            // 만약 다른 이유로 위치 권한이 필요하다면 여기에 추가.
        } else {
            mutableListOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        if (permissionsToRequest.isNotEmpty()){ // 요청할 권한이 있을 때만 요청
            ActivityCompat.requestPermissions(activity, permissionsToRequest.toTypedArray(), requestCode)
        }
    }
}