package com.example.blemodule.data.source.remote

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.blemodule.data.state.AdvertisingState // 수정: state 패키지 사용
import com.example.blemodule.util.Constants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow // 추가: flow 빌더 임포트
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * BLE Advertising(광고) 기능을 관리하는 클래스입니다.
 * 특정 서비스 UUID를 포함하여 기기를 주변에 알립니다.
 * @param context 애플리케이션 컨텍스트
 * @param bluetoothAdapter Bluetooth 어댑터 인스턴스
 */
class BleAdvertiserManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) {
    private val TAG = "BleAdvertiserManager"
    private val advertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser
    private var currentAdvertiseCallback: AdvertiseCallback? = null

    /**
     * BLE 광고를 시작하고 상태 변화를 Flow로 방출합니다.
     * @return AdvertisingState를 방출하는 Flow.
     */
    @SuppressLint("MissingPermission")
    fun startAdvertising(): Flow<AdvertisingState> {
        if (advertiser == null || bluetoothAdapter?.isEnabled == false) {
            val errorMsg = if (advertiser == null) "Advertiser 초기화 안됨" else "블루투스 비활성화됨"
            Log.w(TAG, "광고 시작 불가: $errorMsg")
            return flow { emit(AdvertisingState.Failed(0, errorMsg)) } // flow 빌더 사용
        }
        if (!hasAdvertisePermission()) {
            Log.w(TAG, "광고 시작 불가: 광고 권한 없음")
            return flow { emit(AdvertisingState.Failed(0, "광고 권한 없음")) } // flow 빌더 사용
        }

        return callbackFlow {
            if (currentAdvertiseCallback != null) {
                Log.w(TAG, "이미 광고가 진행 중입니다.")
                trySend(AdvertisingState.Failed(AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED, "이미 시작됨"))
                close()
                return@callbackFlow
            }

            Log.d(TAG, "새로운 광고 시작 시도...")
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build()
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false) // 필요시 true로 변경
                .addServiceUuid(Constants.MESH_SERVICE_UUID)
                .build()

            val advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Log.i(TAG, "광고가 성공적으로 시작되었습니다.")
                    trySend(AdvertisingState.Started)
                }

                override fun onStartFailure(errorCode: Int) {
                    val errorMessage = when (errorCode) {
                        ADVERTISE_FAILED_DATA_TOO_LARGE -> "데이터 크기 초과"
                        ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Advertiser 개수 제한 초과"
                        ADVERTISE_FAILED_ALREADY_STARTED -> "이미 시작됨"
                        ADVERTISE_FAILED_INTERNAL_ERROR -> "내부 오류"
                        ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "광고 기능 미지원"
                        else -> "알 수 없는 광고 오류: $errorCode"
                    }
                    Log.e(TAG, "광고 시작 실패: $errorMessage (Code: $errorCode)")
                    trySend(AdvertisingState.Failed(errorCode, errorMessage))
                    currentAdvertiseCallback = null
                    close()
                }
            }
            currentAdvertiseCallback = advertiseCallback

            Log.d(TAG, "Advertiser에 광고 시작 요청...")
            try {
                advertiser.startAdvertising(settings, data, advertiseCallback)
            } catch (e: SecurityException) {
                Log.e(TAG, "광고 시작 중 보안 예외 발생: ${e.message}")
                trySend(AdvertisingState.Failed(0, "광고 시작 권한 오류"))
                currentAdvertiseCallback = null
                close()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "광고 시작 중 잘못된 상태 오류: ${e.message}")
                trySend(AdvertisingState.Failed(0, "광고 시작 상태 오류"))
                currentAdvertiseCallback = null
                close()
            }

            awaitClose {
                Log.d(TAG, "광고 Flow 종료 요청됨. 광고 중지 시도...")
                stopAdvertisingInternal()
            }
        }
            .onStart { Log.d(TAG, "Advertising Flow 시작") }
            .onCompletion { Log.d(TAG, "Advertising Flow 종료") }
            .flowOn(Dispatchers.IO)
    }

    /** BLE 광고 중지 */
    fun stopAdvertising() {
        stopAdvertisingInternal()
    }

    /** 내부 광고 중지 로직 */
    @SuppressLint("MissingPermission")
    private fun stopAdvertisingInternal() {
        currentAdvertiseCallback?.let { callback ->
            advertiser?.let { adv ->
                if (!hasAdvertisePermission()) {
                    Log.w(TAG, "광고 중지 불가: 광고 권한 없음")
                    return
                }
                try {
                    Log.d(TAG, "Advertiser에 광고 중지 요청...")
                    adv.stopAdvertising(callback)
                    Log.i(TAG, "광고가 성공적으로 중지되었습니다.")
                    currentAdvertiseCallback = null
                } catch (e: SecurityException) {
                    Log.e(TAG, "광고 중지 중 보안 예외 발생: ${e.message}")
                    currentAdvertiseCallback = null
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "광고 중지 중 잘못된 상태 오류: ${e.message}")
                    currentAdvertiseCallback = null
                }
            } ?: Log.w(TAG, "Advertiser 객체가 null 입니다. 광고 중지 불가.")
        } ?: Log.d(TAG, "현재 진행 중인 광고 콜백이 없습니다.")
    }

    /** 필요한 광고 권한 확인 */
    private fun hasAdvertisePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}