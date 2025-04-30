package com.example.blemodule.data.state

import com.example.blemodule.data.model.BleDevice

/**
 * BLE 스캔 작업의 상태 및 결과를 나타내는 봉인 클래스입니다.
 */
sealed class ScanState {
    /** 스캔 작업이 성공적으로 시작되었음을 나타냅니다. */
    object Started : ScanState()

    /** 스캔 중 새로운 기기를 발견했음을 나타냅니다. */
    data class DeviceFound(val device: BleDevice) : ScanState()

    /** 스캔 작업이 실패했음을 나타냅니다. */
    data class Failed(val errorCode: Int, val message: String) : ScanState()

    /** 스캔 작업이 중지되었음을 나타냅니다. (명시적 중지 또는 완료) */
    object Stopped : ScanState()
}