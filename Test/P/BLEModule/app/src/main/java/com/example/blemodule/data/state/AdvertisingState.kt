package com.example.blemodule.data.state

/**
 * BLE 광고(Advertising) 작업의 상태를 나타내는 봉인 클래스입니다.
 */
sealed class AdvertisingState {
    /** 광고 작업이 성공적으로 시작되었음을 나타냅니다. */
    object Started : AdvertisingState()

    /** 광고 작업이 실패했음을 나타냅니다. */
    data class Failed(val errorCode: Int, val message: String) : AdvertisingState()

    /** 광고 작업이 중지되었음을 나타냅니다. (명시적 중지 또는 완료) */
    object Stopped : AdvertisingState()
}