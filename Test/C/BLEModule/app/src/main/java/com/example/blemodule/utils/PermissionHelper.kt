package com.example.blemodule.util

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 권한 체크 및 요청을 간소화하는 헬퍼 클래스
 */
object PermissionHelper {
    /**
     * 주어진 권한들이 모두 허용되었는지 확인
     */
    fun hasPermissions(context: Context, vararg permissions: String): Boolean {
        return permissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Activity에서 런타임 권한 요청
     * @param activity 요청을 시작할 Activity
     * @param permissions 요청할 권한 목록
     * @param requestCode Activity의 onRequestPermissionsResult에서 구분할 코드
     */
    fun requestPermissions(
        activity: Activity,
        permissions: Array<String>,
        requestCode: Int
    ) {
        ActivityCompat.requestPermissions(activity, permissions, requestCode)
    }

    /**
     * 권한 거부 시 재설명(Rationale)이 필요한지 판단
     */
    fun shouldShowRationale(activity: Activity, permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
}
