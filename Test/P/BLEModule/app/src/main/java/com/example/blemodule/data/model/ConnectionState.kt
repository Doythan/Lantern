package com.example.blemodule.data.model

/**
 * BLE 연결 상태를 나타내는 열거형 클래스입니다.
 */
enum class ConnectionState {
    CONNECTING,     // 연결 시도 중
    CONNECTED,      // 연결 성공 및 완료됨
    DISCONNECTING,  // 연결 해제 중
    DISCONNECTED,   // 연결 끊김 (또는 초기 상태)
    FAILED          // 연결 실패
}