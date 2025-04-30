package com.example.blemodule.util

import android.os.ParcelUuid
import java.util.UUID

object Constants {
    /* ---------- UUID ---------- */
    val MESH_SERVICE_UUID: ParcelUuid =
        ParcelUuid.fromString("0000aabb-0000-1000-8000-00805f9b34fb")
    val MESH_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("0000ccdd-0000-1000-8000-00805f9b34fb")
    val CCCD_UUID: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /* ---------- 메시지 구분자 ---------- */
    const val MSG_TYPE_DELIMITER = "::"
    const val PAYLOAD_DELIMITER = "|"
    const val INFO_DEVICE_DELIMITER = ","

    /* ---------- 메시지 타입 ---------- */
    const val MSG_TYPE_INFO = "INFO"
    const val MSG_TYPE_APP  = "APP"

    /* ---------- broadcast / 닉네임 ---------- */
    const val BROADCAST_ID = "BROADCAST"     // 기존 값 유지
    const val MAX_NICKNAME_LENGTH = 12       // ― 추가 ―

    /* ---------- Foreground Service ---------- */
    const val SERVICE_NOTIFICATION_CHANNEL_ID = "BleServiceChannel"
    const val SERVICE_NOTIFICATION_ID = 101
}
