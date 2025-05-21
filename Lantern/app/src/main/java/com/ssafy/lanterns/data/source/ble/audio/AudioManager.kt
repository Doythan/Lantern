package com.ssafy.lanterns.data.source.ble.audio

object AudioManager {
    /** 통화 요청 */
    const val TYPE_CALL_REQUEST: Byte = 0x10
    /** 통화 수락 */
    const val TYPE_CALL_ACCEPT:  Byte = 0x11
    /** 오디오 데이터 프레임 */
    const val TYPE_AUDIO_DATA:   Byte = 0x12
    /** 통화 종료 */
    const val TYPE_CALL_END:     Byte = 0x13
    /** 연결 확인용 하트비트 */
    const val TYPE_HEARTBEAT:    Byte = 0x14
    /** 하트비트 응답 */
    const val TYPE_HEARTBEAT_ACK: Byte = 0x15
}