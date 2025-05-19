package com.ssafy.lanterns.config

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
} 