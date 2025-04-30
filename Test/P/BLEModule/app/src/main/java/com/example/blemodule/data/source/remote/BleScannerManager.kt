package com.example.blemodule.data.source.remote

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.blemodule.data.model.BleDevice
import com.example.blemodule.data.state.ScanState // 수정: state 패키지 사용
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
 * BLE Scanning(탐색) 기능을 관리하는 클래스입니다.
 * 특정 서비스 UUID를 가진 기기를 탐색하고 결과를 Flow 형태로 제공합니다.
 * @param context 애플리케이션 컨텍스트
 * @param bluetoothAdapter Bluetooth 어댑터 인스턴스
 */
class BleScannerManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) {
    private val TAG = "BleScannerManager"
    private val scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private var currentScanCallback: ScanCallback? = null
    private val discoveredDevices = mutableSetOf<String>()

    /**
     * BLE 스캔을 시작하고 결과를 Flow로 방출합니다.
     * @return ScanState를 방출하는 Flow.
     */
    @SuppressLint("MissingPermission")
    fun startScanning(): Flow<ScanState> {
        if (scanner == null || bluetoothAdapter?.isEnabled == false) {
            val errorMsg = if (scanner == null) "Scanner 초기화 안됨" else "블루투스 비활성화됨"
            Log.w(TAG, "스캔 시작 불가: $errorMsg")
            return flow { emit(ScanState.Failed(0, errorMsg)) } // flow 빌더 사용
        }
        if (!hasScanPermission()) {
            Log.w(TAG, "스캔 시작 불가: 스캔 권한 없음")
            return flow { emit(ScanState.Failed(0, "스캔 권한 없음")) } // flow 빌더 사용
        }

        return callbackFlow {
            if (currentScanCallback != null) {
                Log.w(TAG, "이미 스캔이 진행 중입니다.")
                trySend(ScanState.Failed(ScanCallback.SCAN_FAILED_ALREADY_STARTED, "이미 시작됨"))
                close()
                return@callbackFlow
            }

            Log.d(TAG, "새로운 스캔 시작 시도...")
            discoveredDevices.clear()

            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(Constants.MESH_SERVICE_UUID)
                .build()
            val filters = listOf(scanFilter)
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            val scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result?.device?.let { device ->
                        val deviceAddress = device.address
                        if (discoveredDevices.add(deviceAddress)) {
                            val bleDevice = BleDevice(
                                device = device,
                                name = try { device.name } catch (e: SecurityException) { null } ?: "Unknown",
                                address = deviceAddress
                            )
                            Log.i(TAG, "기기 발견: ${bleDevice.name} ($deviceAddress)")
                            trySend(ScanState.DeviceFound(bleDevice))
                        }
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    val errorMessage = when (errorCode) {
                        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "이미 시작됨"
                        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "앱 등록 실패"
                        ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "내부 오류"
                        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "스캔 미지원 기기"
                        ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "하드웨어 리소스 부족"
                        ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "스캔 너무 자주 요청됨"
                        else -> "알 수 없는 스캔 오류: $errorCode"
                    }
                    Log.e(TAG, "스캔 실패: $errorMessage (Code: $errorCode)")
                    trySend(ScanState.Failed(errorCode, errorMessage))
                    currentScanCallback = null
                    close()
                }
            }
            currentScanCallback = scanCallback

            Log.d(TAG, "스캐너에 스캔 시작 요청...")
            try {
                scanner.startScan(filters, settings, scanCallback)
                trySend(ScanState.Started)
                Log.i(TAG, "스캔이 성공적으로 시작되었습니다.")
            } catch (e: SecurityException) {
                Log.e(TAG, "스캔 시작 중 보안 예외 발생: ${e.message}")
                trySend(ScanState.Failed(0, "스캔 시작 권한 오류"))
                currentScanCallback = null
                close()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "스캔 시작 중 잘못된 상태 오류: ${e.message}")
                trySend(ScanState.Failed(0, "스캔 시작 상태 오류"))
                currentScanCallback = null
                close()
            }

            awaitClose {
                Log.d(TAG, "스캔 Flow 종료 요청됨. 스캔 중지 시도...")
                stopScanningInternal()
            }
        }
            .onStart { Log.d(TAG, "Scanning Flow 시작") }
            .onCompletion { Log.d(TAG, "Scanning Flow 종료") }
            .flowOn(Dispatchers.IO)
    }

    /** BLE 스캔 중지 */
    fun stopScanning() {
        stopScanningInternal()
    }

    /** 내부 스캔 중지 로직 */
    @SuppressLint("MissingPermission")
    private fun stopScanningInternal() {
        currentScanCallback?.let { callback ->
            scanner?.let { scn ->
                if (!hasScanPermission()) {
                    Log.w(TAG, "스캔 중지 불가: 스캔 권한 없음")
                    return
                }
                try {
                    Log.d(TAG, "스캐너에 스캔 중지 요청...")
                    scn.stopScan(callback)
                    Log.i(TAG, "스캔이 성공적으로 중지되었습니다.")
                    currentScanCallback = null
                    discoveredDevices.clear()
                } catch (e: SecurityException) {
                    Log.e(TAG, "스캔 중지 중 보안 예외 발생: ${e.message}")
                    currentScanCallback = null
                    discoveredDevices.clear()
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "스캔 중지 중 잘못된 상태 오류: ${e.message}")
                    currentScanCallback = null
                    discoveredDevices.clear()
                }
            } ?: Log.w(TAG, "Scanner 객체가 null 입니다. 스캔 중지 불가.")
        } ?: Log.d(TAG, "현재 진행 중인 스캔 콜백이 없습니다.")
    }

    /** 필요한 스캔 권한 확인 */
    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
}