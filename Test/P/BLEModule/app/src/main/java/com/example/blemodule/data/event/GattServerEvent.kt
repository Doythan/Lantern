package com.example.blemodule.data.event

import android.bluetooth.BluetoothDevice
import com.example.blemodule.data.model.ConnectionState
import com.example.blemodule.data.model.Message
import java.util.UUID

/**
 * GattServerManager 에서 발생하는 이벤트를 나타내는 봉인 클래스입니다.
 */
sealed class GattServerEvent {
    /** 클라이언트 기기와의 연결 상태 변경을 나타냅니다. */
    data class ConnectionChange(val address: String, val device: BluetoothDevice, val state: ConnectionState) : GattServerEvent()

    /** 클라이언트 기기로부터 메시지를 수신했음을 나타냅니다. (Write Request) */
    data class MessageReceived(val address: String, val message: Message) : GattServerEvent()

    /** 클라이언트 기기에게 알림(Notification) 전송 완료 결과를 나타냅니다. */
    data class NotificationSent(val address: String, val success: Boolean) : GattServerEvent()

    /** 클라이언트 기기가 특정 Characteristic의 알림을 구독 시작했음을 나타냅니다. (CCCD 쓰기 결과) */
    data class ClientSubscribed(val address: String, val characteristicUuid: UUID) : GattServerEvent()

    /** 클라이언트 기기가 특정 Characteristic의 알림 구독을 해제했음을 나타냅니다. (CCCD 쓰기 결과) */
    data class ClientUnsubscribed(val address: String, val characteristicUuid: UUID) : GattServerEvent()

    /** GATT 서버 작업 중 오류가 발생했음을 나타냅니다. */
    data class Error(val message: String, val throwable: Throwable? = null) : GattServerEvent()
}