package com.ssafy.lanterns.data.source.ble.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.util.Log
import com.ssafy.lanterns.data.source.ble.gatt.GattClientManager
import com.ssafy.lanterns.data.source.ble.gatt.GattServerManager
import com.ssafy.lanterns.data.source.ble.scanner.ScannerManager
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

/**
 * 프로비저닝 결과 데이터 클래스
 */
data class ProvisionResult(
    val success: Boolean,
    val device: BluetoothDevice? = null,
    val address: Short? = null,
    val deviceKey: ByteArray? = null,
    val networkKey: ByteArray? = null,
    val appKey: ByteArray? = null,
    val error: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as ProvisionResult
        
        if (success != other.success) return false
        if (device?.address != other.device?.address) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = success.hashCode()
        result = 31 * result + (device?.hashCode() ?: 0)
        return result
    }
}

/**
 * 프로비저닝 단계 열거형
 */
enum class ProvisioningStep {
    IDLE,
    DISCOVERING,
    CONNECTING,
    MTU_NEGOTIATION,
    CAPABILITIES_EXCHANGE,
    KEY_EXCHANGE,
    ADDRESS_ASSIGNMENT,
    COMPLETE,
    FAILED
}

/**
 * 프로비저닝 매니저
 * BLE 메시 네트워크의 노드 발견 및 연결 설정을 담당
 * GATT 연결을 통한 키 교환, 주소 할당 등 수행
 */
class ProvisioningManager(
    private val context: Context,
    private val scannerManager: ScannerManager
) {
    private val TAG = "ProvisioningManager"
    
    // 현재 프로비저닝 단계
    private var currentStep = ProvisioningStep.IDLE
    
    // GATT 서버 UUID와 특성 UUID
    private val PROVISIONING_SERVICE_UUID = UUID.fromString("00001827-0000-1000-8000-00805f9b34fb")
    private val PROVISIONING_DATA_IN_UUID = UUID.fromString("00002ADD-0000-1000-8000-00805f9b34fb")
    private val PROVISIONING_DATA_OUT_UUID = UUID.fromString("00002ADD-0000-1000-8000-00805f9b34fb")
    
    // 프로비저닝 연결 타임아웃
    private val PROVISIONING_TIMEOUT = 30000L // 30초
    
    // 마지막으로 할당한 주소 (초기값: 1)
    private var lastAssignedAddress: Short = 1
    
    // 현재 연결된 GATT
    private var gatt: BluetoothGatt? = null
    
    // 프로비저닝 콜백
    private var provisioningContinuation: CancellableContinuation<ProvisionResult>? = null
    
    /**
     * GATT 콜백 구현
     */
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "Connected to GATT server")
                        currentStep = ProvisioningStep.MTU_NEGOTIATION
                        // MTU 협상 시작
                        gatt.requestMtu(517) // 최대 MTU 요청
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "Disconnected from GATT server")
                        gatt.close()
                        if (currentStep != ProvisioningStep.COMPLETE) {
                            currentStep = ProvisioningStep.FAILED
                            resumeWithError("Disconnected from GATT server")
                        }
                    }
                }
            } else {
                Log.e(TAG, "Connection state change error: $status")
                gatt.close()
                currentStep = ProvisioningStep.FAILED
                resumeWithError("Connection error: $status")
            }
        }
        
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "MTU changed to $mtu")
                currentStep = ProvisioningStep.CAPABILITIES_EXCHANGE
                // 서비스 탐색 시작
                gatt.discoverServices()
            } else {
                Log.e(TAG, "MTU change failed: $status")
                failProvisioning("MTU negotiation failed")
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")
                // 프로비저닝 서비스 찾기
                val service = gatt.getService(PROVISIONING_SERVICE_UUID)
                if (service != null) {
                    // Capabilities 읽기 특성 찾기 및 읽기
                    processProvisioningService(gatt, service)
                } else {
                    failProvisioning("Provisioning service not found")
                }
            } else {
                failProvisioning("Service discovery failed")
            }
        }
        
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (characteristic.uuid) {
                    PROVISIONING_DATA_OUT_UUID -> {
                        // Capabilities 수신 처리
                        processCapabilities(value)
                    }
                    else -> Log.d(TAG, "Read from unknown characteristic: ${characteristic.uuid}")
                }
            } else {
                failProvisioning("Characteristic read failed")
            }
        }
        
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (characteristic.uuid) {
                    PROVISIONING_DATA_IN_UUID -> {
                        when (currentStep) {
                            ProvisioningStep.KEY_EXCHANGE -> {
                                Log.d(TAG, "Key exchange data written successfully")
                                // 주소 할당 단계로 이동
                                currentStep = ProvisioningStep.ADDRESS_ASSIGNMENT
                                assignAddress(gatt)
                            }
                            ProvisioningStep.ADDRESS_ASSIGNMENT -> {
                                Log.d(TAG, "Address assignment data written successfully")
                                // 프로비저닝 완료
                                completeProvisioning(gatt.device)
                            }
                            else -> Log.d(TAG, "Write completed in step: $currentStep")
                        }
                    }
                    else -> Log.d(TAG, "Write to unknown characteristic: ${characteristic.uuid}")
                }
            } else {
                failProvisioning("Characteristic write failed")
            }
        }
        
        // API 레벨 호환성 지원
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    when (characteristic.uuid) {
                        PROVISIONING_DATA_OUT_UUID -> {
                            // Capabilities 수신 처리
                            processCapabilities(characteristic.value)
                        }
                        else -> Log.d(TAG, "Read from unknown characteristic: ${characteristic.uuid}")
                    }
                } else {
                    failProvisioning("Characteristic read failed")
                }
            }
        }
    }
    
    /**
     * 프로비저닝 서비스 처리
     */
    @SuppressLint("MissingPermission")
    private fun processProvisioningService(gatt: BluetoothGatt, service: BluetoothGattService) {
        val dataOutChar = service.getCharacteristic(PROVISIONING_DATA_OUT_UUID)
        if (dataOutChar != null) {
            // Capabilities 읽기
            val readSuccessful = gatt.readCharacteristic(dataOutChar)
            if (!readSuccessful) {
                failProvisioning("Failed to read capabilities")
            }
        } else {
            failProvisioning("Provisioning data characteristic not found")
        }
    }
    
    /**
     * Capabilities 처리 및 키 교환 시작
     */
    @SuppressLint("MissingPermission")
    private fun processCapabilities(data: ByteArray) {
        Log.d(TAG, "Received capabilities: ${bytesToHex(data)}")
        
        // Capabilities 파싱 (여기서는 간단하게 처리)
        // 실제로는 디바이스 지원 기능 및 알고리즘 확인 필요
        
        // 키 교환 단계로 이동
        currentStep = ProvisioningStep.KEY_EXCHANGE
        
        // 네트워크 키 및 앱 키 생성
        val networkKey = ByteArray(16)
        Random.nextBytes(networkKey)
        
        val appKey = ByteArray(16)
        Random.nextBytes(appKey)
        
        // 장치 키 생성
        val deviceKey = ByteArray(16)
        Random.nextBytes(deviceKey)
        
        // 키 교환 데이터 구성
        val keyExchangeData = ByteArray(48) // 16(네트워크 키) + 16(앱 키) + 16(장치 키)
        System.arraycopy(networkKey, 0, keyExchangeData, 0, 16)
        System.arraycopy(appKey, 0, keyExchangeData, 16, 16)
        System.arraycopy(deviceKey, 0, keyExchangeData, 32, 16)
        
        // 키 전송
        val service = gatt?.getService(PROVISIONING_SERVICE_UUID)
        val dataInChar = service?.getCharacteristic(PROVISIONING_DATA_IN_UUID)
        
        if (dataInChar != null) {
            // 여기서 실제로는 데이터를 암호화해야 함
            dataInChar.setValue(keyExchangeData)
            val writeSuccessful = gatt?.writeCharacteristic(dataInChar)
            if (writeSuccessful != true) {
                failProvisioning("Failed to write key exchange data")
            }
        } else {
            failProvisioning("Key exchange characteristic not found")
        }
    }
    
    /**
     * 주소 할당
     */
    @SuppressLint("MissingPermission")
    private fun assignAddress(gatt: BluetoothGatt) {
        // 다음 사용 가능한 주소 할당
        val address = ++lastAssignedAddress
        
        // 주소 할당 데이터 구성
        val addressData = ByteArray(2)
        addressData[0] = (address.toInt() and 0xFF).toByte()
        addressData[1] = (address.toInt() shr 8 and 0xFF).toByte()
        
        Log.d(TAG, "Assigning address: $address")
        
        // 주소 전송
        val service = gatt.getService(PROVISIONING_SERVICE_UUID)
        val dataInChar = service?.getCharacteristic(PROVISIONING_DATA_IN_UUID)
        
        if (dataInChar != null) {
            dataInChar.setValue(addressData)
            val writeSuccessful = gatt.writeCharacteristic(dataInChar)
            if (writeSuccessful != true) {
                failProvisioning("Failed to write address data")
            }
        } else {
            failProvisioning("Address assignment characteristic not found")
        }
    }
    
    /**
     * 프로비저닝 완료
     */
    @SuppressLint("MissingPermission")
    private fun completeProvisioning(device: BluetoothDevice) {
        currentStep = ProvisioningStep.COMPLETE
        
        Log.d(TAG, "Provisioning completed successfully for ${device.address}")
        
        // 네트워크 키, 앱 키, 장치 키, 할당 주소를 포함한 결과 생성
        // 실제로는 이전 단계에서 생성된 키를 저장하고 여기서 사용해야 함
        val networkKey = ByteArray(16)
        val appKey = ByteArray(16)
        val deviceKey = ByteArray(16)
        
        val result = ProvisionResult(
            success = true,
            device = device,
            address = lastAssignedAddress,
            deviceKey = deviceKey,
            networkKey = networkKey,
            appKey = appKey
        )
        
        // GATT 연결 종료
        gatt?.close()
        gatt = null
        
        // 프로비저닝 결과 반환
        provisioningContinuation?.resume(result)
        provisioningContinuation = null
    }
    
    /**
     * 프로비저닝 실패 처리
     */
    private fun failProvisioning(errorMessage: String) {
        Log.e(TAG, "Provisioning failed: $errorMessage")
        currentStep = ProvisioningStep.FAILED
        
        // GATT 연결 종료
        gatt?.close()
        gatt = null
        
        // 오류 결과 반환
        resumeWithError(errorMessage)
    }
    
    /**
     * 오류 결과 반환
     */
    private fun resumeWithError(errorMessage: String) {
        provisioningContinuation?.resume(
            ProvisionResult(
                success = false,
                error = errorMessage
            )
        )
        provisioningContinuation = null
    }
    
    /**
     * 노드 발견
     * 주변 BLE 메시 노드를 스캔하여 발견
     * 
     * @return 발견된 노드 목록
     */
    @SuppressLint("MissingPermission")
    suspend fun discoverNodes(): List<BluetoothDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<BluetoothDevice>()
        
        return@withContext suspendCancellableCoroutine { continuation ->
            currentStep = ProvisioningStep.DISCOVERING
            
            val scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device
                    if (!devices.contains(device)) {
                        devices.add(device)
                    }
                }
                
                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "Scan failed with error: $errorCode")
                    continuation.resumeWithException(
                        RuntimeException("BLE Scan failed with error: $errorCode")
                    )
                }
            }
            
            // 스캔 시작
            scannerManager.startHighPowerScanning(scanCallback)
            
            // 타임아웃 후 스캔 중지 및 결과 반환
            continuation.invokeOnCancellation {
                scannerManager.stopScan()
            }
            
            // 5초 동안 스캔 후 결과 반환
            Thread {
                Thread.sleep(5000)
                scannerManager.stopScan()
                continuation.resume(devices)
            }.start()
        }
    }
    
    /**
     * 노드 프로비저닝
     * GATT 연결을 통해 노드를 프로비저닝하고 메시 네트워크에 추가
     * 
     * @param device 프로비저닝할 BluetoothDevice
     * @return 프로비저닝 결과
     */
    @SuppressLint("MissingPermission")
    suspend fun provision(device: BluetoothDevice): ProvisionResult = withContext(Dispatchers.IO) {
        return@withContext withTimeout(PROVISIONING_TIMEOUT) {
            suspendCancellableCoroutine { continuation ->
                currentStep = ProvisioningStep.CONNECTING
                provisioningContinuation = continuation
                
                Log.d(TAG, "Starting provisioning for ${device.address}")
                
                // GATT 연결
                gatt = device.connectGatt(context, false, gattCallback)
                
                continuation.invokeOnCancellation {
                    Log.d(TAG, "Provisioning cancelled")
                    gatt?.disconnect()
                    gatt?.close()
                    gatt = null
                }
            }
        }
    }
    
    /**
     * 바이트 배열을 16진수 문자열로 변환
     */
    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = "0123456789ABCDEF"[v ushr 4]
            hexChars[i * 2 + 1] = "0123456789ABCDEF"[v and 0x0F]
        }
        return String(hexChars)
    }
} 