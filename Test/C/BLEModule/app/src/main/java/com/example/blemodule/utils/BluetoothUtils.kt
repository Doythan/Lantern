package com.example.blemodule.utils

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.widget.Toast
import androidx.annotation.RequiresPermission

object BluetoothUtils {
    fun isBluetoothEnabled(adapter: BluetoothAdapter?): Boolean =
        adapter?.isEnabled == true

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun ensureBluetoothEnabled(activity: Activity, requestCode: Int) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            Toast.makeText(activity, "이 기기는 Bluetooth를 지원하지 않습니다.", Toast.LENGTH_LONG).show()
        } else if (!adapter.isEnabled) {
            activity.startActivityForResult(
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                requestCode
            )
        }
    }
}
