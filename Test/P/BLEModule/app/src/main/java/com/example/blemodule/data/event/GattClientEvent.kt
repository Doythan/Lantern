package com.example.blemodule.data.event

import android.bluetooth.BluetoothDevice
import com.example.blemodule.data.model.ConnectionState
import com.example.blemodule.data.model.Message
import java.util.UUID

/**
 * GattClientManager 에서 발생하는 이벤트를 나타내는 봉인 클래스입니다.
 */
sealed class GattClientEvent {
    /** 원격 GATT 서버와의 연결 상태 변경을 나타냅니다. */
    data class ConnectionChange(val address: String, val state: ConnectionState) : GattClientEvent()

    /** 원격 GATT 서버로부터 메시지(알림)를 수신했음을 나타냅니다. */
    data class MessageReceived(val address: String, val message: Message) : GattClientEvent()

    /** 원격 GATT 서버의 서비스 탐색이 완료되었음을 나타냅니다. */
    data class ServicesDiscovered(val address: String) : GattClientEvent()

    /** Characteristic 알림 설정 상태 변경(CCCD 쓰기 결과)을 나타냅니다. */
    data class NotificationStatus(val address: String, val characteristicUuid: UUID, val enabled: Boolean, val success: Boolean) : GattClientEvent()

    /** Characteristic 쓰기 작업 완료 결과를 나타냅니다. */
    data class WriteResult(val address: String, val characteristicUuid: UUID, val success: Boolean) : GattClientEvent()

    /** GATT 클라이언트 작업 중 오류가 발생했음을 나타냅니다. */
    data class Error(val address: String?, val message: String, val errorCode: Int? = null) : GattClientEvent()
}