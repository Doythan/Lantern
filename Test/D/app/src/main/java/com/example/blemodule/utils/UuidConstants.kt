package com.example.blemodule.utils

// UUID ! BLE 서비스 정의에 꼭 필요해 고정값으로 미리 넣음.
import java.util.UUID

object UuidConstants {
    val LANTERN_SERVICE_UUID: UUID = UUID.fromString("9b5f2c40-24a4-4b53-83b7-4e9a0efb2512")
    val MESSAGE_WRITE_CHARACTERISTIC_UUID: UUID = UUID.fromString("fca3e8ea-9d93-4f67-9a30-3b87f736d5ec")
    val MESSAGE_NOTIFY_CHARACTERISTIC_UUID: UUID = UUID.fromString("bca2c65a-0534-44f7-84eb-00dcf93e956e")
}
