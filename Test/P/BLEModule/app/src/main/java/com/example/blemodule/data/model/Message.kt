package com.example.blemodule.data.model

import com.example.blemodule.util.Constants
import java.nio.charset.StandardCharsets

/**
 * BLE를 통해 교환되는 메시지를 나타내는 봉인 클래스(sealed class)입니다.
 * DeviceInfo와 AppMessage 두 가지 타입의 메시지를 정의합니다.
 */
sealed class Message {
    abstract val rawData: String
    fun toByteArray(): ByteArray = rawData.toByteArray(StandardCharsets.UTF_8)

    /** 네트워크 토폴로지/기기 정보를 나타내는 메시지입니다. */
    data class DeviceInfo(
        val sourceId: String,
        val knownDevices: Set<String>,
        override val rawData: String
    ) : Message()

    /** 애플리케이션 수준 데이터(예: ping/pong)를 나타내는 메시지입니다. */
    data class AppMessage(
        val targetId: String,
        val sourceId: String,
        val payload: String,
        override val rawData: String
    ) : Message()

    companion object {
        /** 바이트 배열 데이터를 파싱하여 Message 객체로 변환합니다. */
        fun fromByteArray(bytes: ByteArray): Message? {
            val messageStr = String(bytes, StandardCharsets.UTF_8)
            return fromString(messageStr)
        }

        /** 문자열을 파싱하여 Message 객체로 변환합니다. */
        private fun fromString(messageStr: String): Message? {
            val parts = messageStr.split(Constants.MSG_TYPE_DELIMITER, limit = 2)
            if (parts.size < 2) return null
            val messageType = parts[0]
            val content = parts[1]
            return when (messageType) {
                Constants.MSG_TYPE_INFO -> {
                    val infoParts = content.split(Constants.PAYLOAD_DELIMITER, limit = 2)
                    if (infoParts.size < 2) return null
                    val devices = infoParts[1].split(Constants.INFO_DEVICE_DELIMITER)
                        .filter { it.isNotBlank() }.toSet()
                    DeviceInfo(infoParts[0], devices, messageStr)
                }
                Constants.MSG_TYPE_APP -> {
                    val appParts = content.split(Constants.PAYLOAD_DELIMITER, limit = 3)
                    if (appParts.size < 3) return null
                    AppMessage(appParts[0], appParts[1], appParts[2], messageStr)
                }
                else -> null
            }
        }

        /** DeviceInfo 메시지 객체를 생성합니다. */
        fun createDeviceInfoMessage(myId: String, knownDevices: Set<String>): DeviceInfo {
            val deviceListStr = knownDevices.joinToString(Constants.INFO_DEVICE_DELIMITER)
            val content = "$myId${Constants.PAYLOAD_DELIMITER}$deviceListStr"
            val rawData = "${Constants.MSG_TYPE_INFO}${Constants.MSG_TYPE_DELIMITER}$content"
            return DeviceInfo(myId, knownDevices, rawData)
        }

        /** AppMessage 메시지 객체를 생성합니다. */
        fun createAppMessage(targetId: String, sourceId: String, payload: String): AppMessage {
            val content = "$targetId${Constants.PAYLOAD_DELIMITER}$sourceId${Constants.PAYLOAD_DELIMITER}$payload"
            val rawData = "${Constants.MSG_TYPE_APP}${Constants.MSG_TYPE_DELIMITER}$content"
            return AppMessage(targetId, sourceId, payload, rawData)
        }
    }
}