package com.ssafy.lanterns.data.source.ble.advertiser

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import com.ssafy.lanterns.data.source.ble.gatt.GattServerManager
import com.ssafy.lanterns.data.source.ble.mesh.MeshMessage
import com.ssafy.lanterns.data.source.ble.mesh.MessageUtils
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * BLE 광고를 통한 메시지 송신을 관리하는 클래스
 * TTL 기반의 메시지 전파 기능 제공
 */
class AdvertiserManager(
    private val context: Context,
    private var nickname: String? = null
) {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private val isAdvertising = AtomicBoolean(false)
    private val TAG = "AdvertiserManager"
    private val handler = Handler(Looper.getMainLooper())
    
    // 고정 제조사 ID - Google 또는 삼성 같은 널리 알려진 ID 사용 가능
    companion object {
        const val MANUFACTURER_ID = 0x0001 // 제조사 ID 예시
        
        // 서비스 UUID 정의
        val SERVICE_UUID: UUID = UUID.fromString("4d61-72b0-4e65-b208-6adff42f5624")
        
        // 프레즌스 브로드캐스트 관련 상수
        const val PRESENCE_TTL = 2 // TTL 값
        const val PRESENCE_BROADCAST_INTERVAL = 30000L // 30초 간격
        
        // 시퀀스 번호 생성을 위한 변수
        private var lastSequenceNumber = 0
        
        // 광고 지속 시간 (3초)
        const val ADVERTISE_TIMEOUT = 3000L
        
        // 재시도 지연 시간 (300ms)
        const val RETRY_DELAY = 300L
        
        // 광고 간격 (500ms)
        const val ADVERTISE_INTERVAL = 500L
    }
    
    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (bluetoothLeAdvertiser == null) {
            Log.e(TAG, "BluetoothLeAdvertiser is not available.")
        }
        
        // BLUETOOTH_CONNECT 권한 확인 후 저장된 닉네임 로드
        val sharedPrefs = context.getSharedPreferences("lantern_prefs", Context.MODE_PRIVATE)
        nickname = if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            sharedPrefs.getString("device_nickname", bluetoothAdapter?.name)
        } else {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot get device name.")
            sharedPrefs.getString("device_nickname", "Unknown")
        }
    }
    
    /**
     * 닉네임 설정
     * 닉네임이 변경되고 광고 중이라면 광고를 재시작하여 닉네임을 반영
     */
    fun setNickname(newNickname: String) {
        this.nickname = newNickname
        if (isAdvertising.get()) {
            stopAdvertising()
            startAdvertising()
        }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising(){
        if (isAdvertising.get()) {
            Log.d(TAG, "Advertising is already active.")
            return
        }
        if (bluetoothLeAdvertiser == null) {
            Log.e(TAG, "BluetoothLeAdvertiser not initialized. Cannot start advertising.")
            return
        }
        
        // 필요한 권한 확인
        val hasAdvertisePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        val hasConnectPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        
        // 필요한 권한이 없으면 광고를 시작하지 않음
        if (!hasAdvertisePermission) {
            Log.e(TAG, "Permission denied: BLUETOOTH_ADVERTISE. Cannot start advertising.")
            return
        }

        // Advertise Setting
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        // 랜턴 UUID
        val parcelUuid = ParcelUuid(SERVICE_UUID)

        // Advertising 시 소량의 데이터
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true) // 닉네임이 기기 이름으로 설정되도록 함
            .addServiceUuid(parcelUuid)
            .build()
            
        // 닉네임을 기기 이름으로 설정
        // BLUETOOTH_CONNECT 권한이 있을 때만 닉네임 설정 시도
        if (hasConnectPermission && nickname != null) {
            try {
                bluetoothAdapter?.name = nickname
                Log.d(TAG, "Device name set to: $nickname")
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception while setting device name", e)
            }
        } else {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot set device name.")
        }

        // advertise 성공 실패 코드
        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                super.onStartSuccess(settingsInEffect)
                isAdvertising.set(true)
                Log.i(TAG, "Advertising started successfully.")
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                isAdvertising.set(false)
                val errorReason = when (errorCode) {
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data Too Large"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too Many Advertisers"
                    ADVERTISE_FAILED_ALREADY_STARTED -> "Already Started"
                    ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal Error"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature Unsupported"
                    else -> "Unknown Error ($errorCode)"
                }
                Log.e(TAG, "Advertising failed: $errorReason")
            }
        }

        try {
            // 광고 시작
            bluetoothLeAdvertiser?.startAdvertising(settings, advertiseData, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while starting advertising", e)
            isAdvertising.set(false)
        } catch (e: Exception) {
            Log.e(TAG, "Exception while starting advertising", e)
            isAdvertising.set(false)
        }
    }
    
    /**
     * 특정 데이터를 포함한 광고 시작
     * Mesh Network를 위한 메소드로, 광고 데이터에 Mesh PDU를 포함
     * 
     * @param data 광고 데이터에 포함될 바이트 배열 (Mesh PDU)
     */
    @SuppressLint("MissingPermission")
    fun startAdvertisingWithData(data: ByteArray) {
        if (isAdvertising.get()) {
            Log.d(TAG, "Advertising is already active. Stopping current advertising...")
            stopAdvertising()
        }
        
        if (bluetoothLeAdvertiser == null) {
            Log.e(TAG, "BluetoothLeAdvertiser not initialized. Cannot start advertising.")
            return
        }
        
        // 필요한 권한 확인
        val hasAdvertisePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        val hasConnectPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        
        // 필요한 권한이 없으면 광고를 시작하지 않음
        if (!hasAdvertisePermission) {
            Log.e(TAG, "Permission denied: BLUETOOTH_ADVERTISE. Cannot start advertising.")
            return
        }
        
        // Advertise Setting
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()
        
        // 랜턴 UUID
        val parcelUuid = ParcelUuid(SERVICE_UUID)
        
        try {
            // ManufacturerSpecificData에 Mesh PDU 포함
            // 현재 31바이트 제한 때문에 모든 데이터를 포함하지 못할 수 있음
            // 필요한 경우 TransportLayer에서 분할된 작은 데이터를 받아야 함
            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false) // 공간 확보를 위해 기기 이름 제외
                .addServiceUuid(parcelUuid)
                .addManufacturerData(MANUFACTURER_ID, getManufacturerData(data))
                .build()
            
            // 닉네임을 기기 이름으로 설정
            // BLUETOOTH_CONNECT 권한이 있을 때만 닉네임 설정 시도
            if (hasConnectPermission && nickname != null) {
                try {
                    bluetoothAdapter?.name = nickname
                    Log.d(TAG, "Device name set to: $nickname")
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception while setting device name", e)
                }
            } else {
                Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot set device name.")
            }
            
            // advertise 성공 실패 코드
            advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    super.onStartSuccess(settingsInEffect)
                    isAdvertising.set(true)
                    Log.i(TAG, "Advertising with data started successfully.")
                }

                override fun onStartFailure(errorCode: Int) {
                    super.onStartFailure(errorCode)
                    isAdvertising.set(false)
                    val errorReason = when (errorCode) {
                        ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data Too Large"
                        ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too Many Advertisers"
                        ADVERTISE_FAILED_ALREADY_STARTED -> "Already Started"
                        ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal Error"
                        ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature Unsupported"
                        else -> "Unknown Error ($errorCode)"
                    }
                    Log.e(TAG, "Advertising with data failed: $errorReason")
                    
                    // 데이터가 너무 큰 경우, 기본 광고로 폴백
                    if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE) {
                        Log.w(TAG, "Falling back to default advertising without data")
                        startAdvertising()
                    }
                }
            }
            
            // 광고 시작
            try {
                bluetoothLeAdvertiser?.startAdvertising(settings, advertiseData, advertiseCallback)
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception while starting advertising with data", e)
                isAdvertising.set(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting advertising with data", e)
            isAdvertising.set(false)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        if (!isAdvertising.get() || bluetoothLeAdvertiser == null || advertiseCallback == null) {
            return
        }
        
        // 필요한 권한 확인
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission denied: BLUETOOTH_ADVERTISE. Cannot stop advertising safely.")
            // 권한이 없어도 내부 상태는 변경
            isAdvertising.set(false)
            return
        }
        
        try {
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            Log.d(TAG, "Advertising stopped")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while stopping advertising", e)
        } catch (e: Exception) {
            Log.e(TAG, "Exception while stopping advertising", e)
        } finally {
            isAdvertising.set(false)
        }
    }
    
    /**
     * 제조사 데이터 형식으로 변환
     * Mesh PDU를 제조사 데이터(Manufacturer Data) 형식으로 변환
     * 31바이트 제한으로 인해 데이터가 큰 경우 잘릴 수 있음
     */
    private fun getManufacturerData(data: ByteArray): ByteArray {
        // 기본 헤더 추가 (필요시 수정)
        val MAX_ADVERTISE_DATA_SIZE = 23 // 31바이트 - 헤더(8바이트)
        val dataSize = minOf(data.size, MAX_ADVERTISE_DATA_SIZE)
        
        return ByteBuffer.allocate(dataSize)
            .put(data, 0, dataSize)
            .array()
    }
    
    /**
     * 광고 데이터 압축
     * @param data 압축할 데이터
     * @return 압축된 데이터
     */
    private fun compressMessage(data: ByteArray): ByteArray {
        // 실제로는 여기에 압축 알고리즘 구현
        // 예를 들어 GZIP 등을 사용할 수 있음
        // 현재는 간단히 원본 데이터 반환
        return data
    }
    
    /**
     * 압축 해제
     * @param data 압축 해제할 데이터
     * @return 압축 해제된 데이터
     */
    private fun decompressMessage(data: ByteArray): ByteArray {
        // 실제로는 여기에 압축 해제 알고리즘 구현
        // 압축 알고리즘에 맞는 해제 로직 필요
        // 현재는 간단히 원본 데이터 반환
        return data
    }
    
    /**
     * 중복 메시지 확인
     * @param sender 발신자 주소
     * @param sequenceNumber 시퀀스 번호
     * @return 중복 여부
     */
    private fun isDuplicateMessage(_sender: String, _sequenceNumber: Int): Boolean {
        // 실제로는 최근 메시지 캐시를 사용하여 중복 검사
        // 현재는 간단히 false 반환
        return false
    }
    
    /**
     * 시퀀스 번호 생성
     * @return 새로운 시퀀스 번호
     */
    private fun generateSequenceNumber(): Int {
        // 단순히 증가하는 카운터로 구현
        // 실제로는 롤오버 처리 등이 필요
        synchronized(this) {
            lastSequenceNumber = (lastSequenceNumber + 1) % 65536
            return lastSequenceNumber
        }
    }
    
    /**
     * 메시지 광고 시작
     * @param message 전송할 메시지
     * @param timeout 광고 지속 시간 (밀리초, 기본값 3초)
     */
    fun startAdvertising(message: MeshMessage, timeout: Long = ADVERTISE_TIMEOUT) {
        if (bluetoothLeAdvertiser == null) {
            Log.e(TAG, "Bluetooth LE Advertiser를 사용할 수 없습니다")
            return
        }
        
        if (isAdvertising.get()) {
            stopAdvertising()
        }
        
        try {
            // 메시지를 바이트 배열로 직렬화
            val messageBytes = message.toBytes()
            
            // 직렬화된 데이터를 압축 (31바이트 제한 대응)
            val compressedData = compressMessage(messageBytes)
            
            // 광고 설정
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(timeout.toInt())
                .build()
            
            // 광고 데이터
            val advertiseData = AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid(SERVICE_UUID))
                .addServiceData(ParcelUuid(SERVICE_UUID), compressedData)
                .build()
            
            // 광고 시작
            bluetoothLeAdvertiser?.startAdvertising(settings, advertiseData, advertiseCallback)
            
            // 타임아웃 설정
            handler.postDelayed({ stopAdvertising() }, timeout)
            
            Log.d(TAG, "메시지 광고 시작: ttl=${message.ttl}, seq=${message.sequenceNumber}")
        } catch (e: Exception) {
            Log.e(TAG, "광고 시작 실패", e)
        }
    }
    
    /**
     * 존재 알림 메시지 광고
     * @param deviceAddress 기기 주소
     * @param nickname 닉네임
     */
    fun broadcastPresence(deviceAddress: String, nickname: String) {
        val presenceMessage = MeshMessage(
            sequenceNumber = generateSequenceNumber(),
            ttl = PRESENCE_TTL,
            timestamp = System.currentTimeMillis(),
            sender = deviceAddress,
            senderNickname = nickname,
            target = null,
            content = "",
            messageType = com.ssafy.lanterns.data.source.ble.mesh.MessageType.PRESENCE
        )
        
        startAdvertising(presenceMessage, PRESENCE_BROADCAST_INTERVAL)
    }
}