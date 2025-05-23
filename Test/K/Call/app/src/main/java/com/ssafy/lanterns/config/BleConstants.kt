package com.ssafy.lanterns.config

import java.util.UUID

/**
 * BLE 관련 상수를 정의하는 클래스
 */
object BleConstants {
    // 제조사 ID - 랜턴 앱 전용 식별자
    // 0x02E0 (736)은 현재 앱에서 사용하는 메시지 ID
    const val LANTERN_MANUFACTURER_ID_MESSAGE = 0x02E0 
    
    // 0x0059 (89)는 이전 앱에서 사용하던 유저 정보 ID
    const val MANUFACTURER_ID_USER = 0x0059 
    
    // 아래는 향후 확장을 위한 ID
    const val LANTERN_MANUFACTURER_ID_EMAIL = 0xFFFE
    const val MANUFACTURER_ID_LOCATION = 0xFFFE

    // 패킷 데이터 타입 및 버전 (향후 확장성 고려)
    const val DATA_TYPE_LANTERN_V1: Byte = 0x01  // 랜턴 앱 데이터 타입
    const val PROTOCOL_VERSION_V1: Byte = 0x01   // 프로토콜 버전

    // 데이터 필드 바이트 크기 정의
    const val SERVER_USER_ID_BYTES = 4  // 4바이트 (Int)
    const val MAX_NICKNAME_BYTES_ADV = 20
    const val DEPTH_BYTES = 1             // Depth 정보 (0-255)
    const val MAX_ADV_DATA_SIZE = 31      // BLE 광고 데이터 최대 크기
    
    // 통화 관련 UUID 정의
    // 통화 서비스 UUID: 0000FE10-0000-1000-8000-00805F9B34FB (고유한 값으로 설정)
    val CALL_SERVICE_UUID: UUID = UUID.fromString("0000FE10-0000-1000-8000-00805F9B34FB")
    
    // 음성 데이터 특성 UUID (클라이언트 -> 서버) 
    val AUDIO_STREAM_CLIENT_TO_SERVER_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FE11-0000-1000-8000-00805F9B34FB")
    
    // 음성 데이터 특성 UUID (서버 -> 클라이언트)
    val AUDIO_STREAM_SERVER_TO_CLIENT_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FE12-0000-1000-8000-00805F9B34FB")
    
    // 통화 제어 특성 UUID (통화 시작, 종료 등의 제어 명령)
    val CALL_CONTROL_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FE13-0000-1000-8000-00805F9B34FB")
    
    // 통화 상태 특성 UUID (현재 통화 상태 정보)
    val CALL_STATE_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FE14-0000-1000-8000-00805F9B34FB")
    
    // Client Characteristic Configuration Descriptor (CCCD) UUID - 표준 UUID
    val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    
    // 통화 제어 명령 상수
    const val CALL_COMMAND_INVITE: Byte = 0x01    // 통화 초대
    const val CALL_COMMAND_ACCEPT: Byte = 0x02    // 통화 수락
    const val CALL_COMMAND_REJECT: Byte = 0x03    // 통화 거절
    const val CALL_COMMAND_END: Byte = 0x04       // 통화 종료
    const val CALL_COMMAND_MUTE_ON: Byte = 0x05   // 음소거 켜기
    const val CALL_COMMAND_MUTE_OFF: Byte = 0x06  // 음소거 끄기
    
    // 반이중 통신(턴 제어) 명령 상수
    const val CALL_COMMAND_VOICE_START_REQUEST: Byte = 0x10  // 발언권 요청
    const val CALL_COMMAND_VOICE_START_GRANT: Byte = 0x11    // 발언권 허용
    const val CALL_COMMAND_VOICE_END: Byte = 0x12            // 발언 종료
    const val CALL_COMMAND_INTERRUPT_REQUEST: Byte = 0x13    // 상대방 발언 중단 요청
    
    // 통화 상태 상수
    const val CALL_STATE_IDLE: Byte = 0x00           // 통화 대기
    const val CALL_STATE_INVITING: Byte = 0x01       // 통화 초대 중
    const val CALL_STATE_RINGING: Byte = 0x02        // 통화 벨 울림
    const val CALL_STATE_CONNECTED: Byte = 0x03      // 통화 연결됨
    const val CALL_STATE_CONNECTED_MY_TURN: Byte = 0x04  // 통화 연결됨 (내 턴)
    const val CALL_STATE_CONNECTED_OTHER_TURN: Byte = 0x05  // 통화 연결됨 (상대방 턴)
    
    // 오디오 관련 상수
    const val AUDIO_SAMPLE_RATE = 16000  // 음성 통화에 적합한 샘플링 속도 (16kHz)
    const val AUDIO_CHANNEL_CONFIG = 1    // 모노 채널
    const val AUDIO_ENCODING = 2          // PCM 16bit
    const val AUDIO_BUFFER_SIZE = 1024    // 오디오 버퍼 크기
} 