package com.ssafy.lanterns.config

object NeighborDiscoveryConstants {
    // BLE 광고 및 스캔 주기 설정 (ms)
    const val ADVERTISE_INTERVAL_MS = 2000L       // 2초마다 광고 (배터리 절약)
    const val BLE_SCAN_INTERVAL_MS = 1000L        // 1초마다 스캔 결과 처리 (더 효율적)
    const val DEVICE_EXPIRATION_MS = 60000L       // 60초 동안 업데이트 없으면 목록에서 제거 (이전 30초에서 변경)
    const val UI_UPDATE_INTERVAL_MS = 30000L      // 30초 간격으로 UI 업데이트 (레이더 위치 변화 최소화)
    
    // 간헐적 스캔 관련 상수
    const val SCAN_DURATION_MS = 4000L            // 4초 동안 스캔
    const val SCAN_INTERVAL_MS = 8000L            // 8초마다 스캔 시작 (4초 스캔 + 4초 대기, 배터리 절약)

    // 레이더 UI 관련 상수
    const val MAX_DISPLAY_DEPTH_INITIAL = 3       // 초기 레이더 표시 최대 Depth
    val DISPLAY_DEPTH_LEVELS = listOf(1, 2, 3, 5, 7, 10) // UI에서 선택 가능한 Depth 레벨
    const val MAX_TRACKABLE_DEPTH = 20            // 시스템이 추적하거나 광고에 반영할 수 있는 최대 Depth (무한 증가 방지)
} 