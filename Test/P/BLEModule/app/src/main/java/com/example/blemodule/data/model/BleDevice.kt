package com.example.blemodule.data.model

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice

/**
 * BLE 장치를 나타내는 데이터 클래스입니다.
 * @property device 원본 Android BluetoothDevice 객체
 * @property name 장치 이름 (권한 문제 등으로 null일 수 있음)
 * @property address 장치의 고유 MAC 주소
 * @property connectionState 현재 장치와의 연결 상태
 */
data class BleDevice(
    val device: BluetoothDevice,
    val name: String?,
    val address: String,
    var connectionState: ConnectionState = ConnectionState.DISCONNECTED // 초기 상태는 연결 끊김
) {
    /** 두 BleDevice 객체가 동일한지 비교합니다. (주소 기준) */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BleDevice
        return address == other.address
    }

    /** 객체의 해시 코드를 반환합니다. (주소 기반) */
    override fun hashCode(): Int {
        return address.hashCode()
    }

    /** 디버깅 등을 위해 객체를 문자열로 표현합니다. */
    override fun toString(): String {
        return "BleDevice(name=$name, address='$address', state=$connectionState)"
    }

    /** BluetoothDevice 객체로부터 장치 이름을 안전하게 가져옵니다. */
    @SuppressLint("MissingPermission") // 권한 확인은 사용하는 쪽에서 처리해야 함
    fun getDeviceNameSafe(): String {
        return try {
            device.name ?: "Unknown"
        } catch (e: SecurityException) {
            "Unknown (No Permission)"
        }
    }
}