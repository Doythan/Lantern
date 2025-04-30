package com.example.blemodule.data.source.remote

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.blemodule.data.event.GattClientEvent // 수정: event 패키지 사용
import com.example.blemodule.data.model.ConnectionState
import com.example.blemodule.data.model.Message
import com.example.blemodule.util.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 원격 BLE 기기(GATT 서버 역할)에 대한 클라이언트 연결 및 통신을 관리합니다.
 * 여러 기기에 동시에 연결하고 관리할 수 있습니다.
 * @param context 애플리케이션 컨텍스트
 */
@SuppressLint("MissingPermission") // 권한 확인은 각 함수 내부에서 수행
class GattClientManager(private val context: Context) {
    private val TAG = "GattClientManager"
    // 시스템 Bluetooth 서비스 접근 매니저
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    // Bluetooth 어댑터 (기기의 블루투스 하드웨어 제어)
    private val bluetoothAdapter = bluetoothManager?.adapter

    // 활성 GATT 연결 관리 (Key: MAC 주소, Value: BluetoothGatt 객체)
    private val activeGattConnections = ConcurrentHashMap<String, BluetoothGatt>()
    // 연결 시도 중인 기기 주소 관리 (중복 연결 시도 방지)
    private val connectingDevices = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // 내부 CoroutineScope 정의 (SupervisorJob 사용)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // GATT 이벤트를 외부(Repository 등)로 전달하기 위한 SharedFlow
    private val _gattEvents = MutableSharedFlow<GattClientEvent>(
        replay = 0, extraBufferCapacity = 10, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val gattEvents: SharedFlow<GattClientEvent> = _gattEvents.asSharedFlow()

    // 연결 타임아웃 처리를 위한 메인 스레드 핸들러
    private val handler = Handler(Looper.getMainLooper())
    private val connectionTimeoutMillis = 15000L // 15초 연결 타임아웃

    /**
     * 특정 BluetoothDevice 객체에 GATT 연결을 시도합니다.
     * @param device 연결할 BluetoothDevice 객체
     */
    fun connect(device: BluetoothDevice) {
        val deviceAddress = device.address
        if (!hasConnectPermission()) {
            Log.w(TAG, "연결 불가 ($deviceAddress): BLUETOOTH_CONNECT 권한 없음")
            emitEvent(GattClientEvent.Error(deviceAddress, "연결 권한 없음"))
            return
        }
        if (activeGattConnections.containsKey(deviceAddress) || connectingDevices.contains(deviceAddress)) {
            Log.w(TAG, "이미 연결되었거나 연결 시도 중인 기기: $deviceAddress")
            return
        }

        Log.i(TAG, "GATT 연결 시도 -> ${getDeviceName(device)} ($deviceAddress)")
        connectingDevices.add(deviceAddress)
        emitEvent(GattClientEvent.ConnectionChange(deviceAddress, ConnectionState.CONNECTING))

        try {
            // GATT 연결 시작 (autoConnect=false, Transport=LE)
            val gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) {
                Log.e(TAG, "connectGatt 호출 실패 ($deviceAddress). 반환값 null.")
                connectingDevices.remove(deviceAddress)
                emitEvent(GattClientEvent.ConnectionChange(deviceAddress, ConnectionState.FAILED))
                emitEvent(GattClientEvent.Error(deviceAddress, "connectGatt 호출 실패"))
            } else {
                // 연결 타임아웃 설정
                handler.postDelayed({
                    if (connectingDevices.contains(deviceAddress)) {
                        Log.w(TAG, "연결 타임아웃: $deviceAddress")
                        connectingDevices.remove(deviceAddress)
                        emitEvent(GattClientEvent.ConnectionChange(deviceAddress, ConnectionState.FAILED))
                        emitEvent(GattClientEvent.Error(deviceAddress, "연결 타임아웃"))
                        closeGatt(gatt) // 타임아웃 시 gatt 객체 정리
                    }
                }, deviceAddress, connectionTimeoutMillis) // 주소를 토큰으로 사용
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "connectGatt 보안 예외 ($deviceAddress): ${e.message}")
            connectingDevices.remove(deviceAddress)
            emitEvent(GattClientEvent.ConnectionChange(deviceAddress, ConnectionState.FAILED))
            emitEvent(GattClientEvent.Error(deviceAddress, "연결 권한 오류"))
        }
    }

    /**
     * 특정 주소의 기기와의 GATT 연결을 해제합니다.
     * @param address 연결 해제할 기기의 MAC 주소
     */
    fun disconnect(address: String) {
        if (!hasConnectPermission()) {
            Log.w(TAG, "연결 해제 불가 ($address): BLUETOOTH_CONNECT 권한 없음")
            emitEvent(GattClientEvent.Error(address, "연결 해제 권한 없음"))
            return
        }
        activeGattConnections[address]?.let { gatt ->
            Log.i(TAG, "GATT 연결 해제 요청 -> $address")
            emitEvent(GattClientEvent.ConnectionChange(address, ConnectionState.DISCONNECTING))
            gatt.disconnect() // 비동기 호출
        } ?: Log.w(TAG, "연결 해제 시도: 활성 연결 없음 ($address)")
    }

    /** 모든 활성 GATT 연결을 해제합니다. */
    fun disconnectAll() {
        if (!hasConnectPermission()) {
            Log.w(TAG, "전체 연결 해제 불가: BLUETOOTH_CONNECT 권한 없음")
            return
        }
        Log.i(TAG, "모든 GATT 연결 해제 요청...")
        activeGattConnections.keys.toList().forEach { address -> disconnect(address) }
    }

    /**
     * 특정 GATT 연결의 서비스를 탐색합니다. (연결 성공 후 호출됨)
     * @param gatt 서비스 탐색을 수행할 BluetoothGatt 객체
     */
    private fun discoverServices(gatt: BluetoothGatt) {
        val deviceAddress = gatt.device.address
        if (!hasConnectPermission()) {
            Log.w(TAG, "서비스 탐색 불가 ($deviceAddress): BLUETOOTH_CONNECT 권한 없음")
            emitEvent(GattClientEvent.Error(deviceAddress, "서비스 탐색 권한 없음"))
            disconnect(deviceAddress)
            return
        }
        Log.d(TAG, "서비스 탐색 시작 -> $deviceAddress")
        if (!gatt.discoverServices()) {
            Log.e(TAG, "서비스 탐색 시작 실패: $deviceAddress")
            emitEvent(GattClientEvent.Error(deviceAddress, "서비스 탐색 시작 실패"))
            disconnect(deviceAddress)
        }
    }

    /**
     * 지정된 Characteristic에 대한 알림(Notification)을 활성화합니다.
     * @param address 대상 기기 주소
     * @param serviceUuid 서비스 UUID
     * @param characteristicUuid Characteristic UUID
     */
    fun enableNotifications(address: String, serviceUuid: UUID, characteristicUuid: UUID) {
        setNotificationState(address, serviceUuid, characteristicUuid, true)
    }

    /**
     * 지정된 Characteristic에 대한 알림(Notification)을 비활성화합니다.
     * @param address 대상 기기 주소
     * @param serviceUuid 서비스 UUID
     * @param characteristicUuid Characteristic UUID
     */
    fun disableNotifications(address: String, serviceUuid: UUID, characteristicUuid: UUID) {
        setNotificationState(address, serviceUuid, characteristicUuid, false)
    }

    /**
     * Characteristic 알림 상태를 설정합니다. (활성화/비활성화 공통 로직)
     */
    private fun setNotificationState(address: String, serviceUuid: UUID, characteristicUuid: UUID, enable: Boolean) {
        val gatt = activeGattConnections[address] ?: run {
            Log.w(TAG, "알림 설정 불가 ($address): 활성 연결 없음")
            emitEvent(GattClientEvent.Error(address, "알림 설정 실패: 연결 없음"))
            return
        }
        if (!hasConnectPermission()) {
            Log.w(TAG, "알림 설정 불가 ($address): BLUETOOTH_CONNECT 권한 없음")
            emitEvent(GattClientEvent.Error(address, "알림 설정 권한 없음"))
            return
        }
        val characteristic = findCharacteristic(gatt, serviceUuid, characteristicUuid) ?: return

        // 1. 로컬 알림 설정
        if (!gatt.setCharacteristicNotification(characteristic, enable)) {
            Log.e(TAG, "setCharacteristicNotification($enable) 실패: $address / $characteristicUuid")
            emitEvent(GattClientEvent.NotificationStatus(address, characteristicUuid, enable, false))
            return
        }

        // 2. CCCD 쓰기
        val cccdUuid = Constants.CCCD_UUID
        val descriptor = characteristic.getDescriptor(cccdUuid) ?: run {
            Log.w(TAG, "CCCD($cccdUuid) 찾을 수 없음: $address / $characteristicUuid")
            emitEvent(GattClientEvent.Error(address, "알림 설정 실패: CCCD 없음"))
            return
        }
        val value = if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        Log.d(TAG, "CCCD 쓰기 요청 ($address / $characteristicUuid): ${if(enable)"활성화" else "비활성화"}")
        writeDescriptor(gatt, descriptor, value)
    }


    /**
     * 지정된 Characteristic에 데이터를 씁니다.
     * @param address 대상 기기 주소
     * @param serviceUuid 서비스 UUID
     * @param characteristicUuid Characteristic UUID
     * @param data 쓸 바이트 배열 데이터
     * @param writeType 쓰기 타입 (기본값: WRITE_TYPE_DEFAULT - 응답 필요)
     */
    fun writeCharacteristic(
        address: String,
        serviceUuid: UUID,
        characteristicUuid: UUID,
        data: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    ) {
        val gatt = activeGattConnections[address] ?: run {
            Log.w(TAG, "쓰기 불가 ($address): 활성 연결 없음")
            emitEvent(GattClientEvent.Error(address, "쓰기 실패: 연결 없음"))
            return
        }
        if (!hasConnectPermission()) {
            Log.w(TAG, "쓰기 불가 ($address): BLUETOOTH_CONNECT 권한 없음")
            emitEvent(GattClientEvent.Error(address, "쓰기 권한 없음"))
            return
        }
        val characteristic = findCharacteristic(gatt, serviceUuid, characteristicUuid) ?: return

        // 쓰기 속성 확인
        val properties = characteristic.properties
        val supportsWrite = (properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
        val supportsWriteNoResponse = (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0

        if (!supportsWrite && !supportsWriteNoResponse) {
            Log.w(TAG, "쓰기 불가 ($address): Characteristic $characteristicUuid 쓰기 속성 없음")
            emitEvent(GattClientEvent.Error(address, "쓰기 실패: 속성 없음"))
            return
        }
        // 요청된 쓰기 타입 지원 여부 확인 (선택적)
        if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT && !supportsWrite) {
            Log.w(TAG, "쓰기 불가 ($address): Characteristic $characteristicUuid 가 WRITE_TYPE_DEFAULT 미지원")
            emitEvent(GattClientEvent.Error(address, "쓰기 실패: 쓰기 타입 미지원"))
            return
        }
        if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE && !supportsWriteNoResponse) {
            Log.w(TAG, "쓰기 불가 ($address): Characteristic $characteristicUuid 가 WRITE_TYPE_NO_RESPONSE 미지원")
            emitEvent(GattClientEvent.Error(address, "쓰기 실패: 쓰기 타입 미지원"))
            return
        }


        // API 레벨에 따른 쓰기 방식 분기
        characteristic.writeType = writeType
        val writeResult: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "API 33+ 쓰기 요청 ($address / $characteristicUuid)")
            writeResult = gatt.writeCharacteristic(characteristic, data, writeType)
        } else {
            Log.d(TAG, "API 32- 쓰기 요청 ($address / $characteristicUuid)")
            @Suppress("DEPRECATION")
            characteristic.value = data
            @Suppress("DEPRECATION")
            val success = gatt.writeCharacteristic(characteristic)
            writeResult = if (success) BluetoothStatusCodes.SUCCESS else BluetoothStatusCodes.ERROR_UNKNOWN
        }

        if (writeResult != BluetoothStatusCodes.SUCCESS) {
            Log.e(TAG, "writeCharacteristic 요청 실패 ($address / $characteristicUuid), status=$writeResult")
            emitEvent(GattClientEvent.WriteResult(address, characteristicUuid, false))
        } else {
            Log.d(TAG, "writeCharacteristic 요청 성공 ($address / $characteristicUuid)")
        }
    }

    /**
     * 지정된 Descriptor에 데이터를 씁니다. (주로 CCCD 설정에 사용)
     * @param gatt BluetoothGatt 객체
     * @param descriptor 쓸 대상 Descriptor
     * @param value 쓸 바이트 배열 데이터
     */
    private fun writeDescriptor(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray) {
        if (!hasConnectPermission()) {
            Log.w(TAG, "Descriptor 쓰기 불가 (${gatt.device.address}): BLUETOOTH_CONNECT 권한 없음")
            emitEvent(GattClientEvent.Error(gatt.device.address, "Descriptor 쓰기 권한 없음"))
            return
        }

        val writeResult: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "API 33+ Descriptor 쓰기 요청 (${gatt.device.address} / ${descriptor.uuid})")
            writeResult = gatt.writeDescriptor(descriptor, value)
        } else {
            Log.d(TAG, "API 32- Descriptor 쓰기 요청 (${gatt.device.address} / ${descriptor.uuid})")
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            val success = gatt.writeDescriptor(descriptor)
            writeResult = if (success) BluetoothStatusCodes.SUCCESS else BluetoothStatusCodes.ERROR_UNKNOWN
        }

        if (writeResult != BluetoothStatusCodes.SUCCESS) {
            Log.e(TAG, "writeDescriptor 요청 실패 (${gatt.device.address} / ${descriptor.uuid}), status=$writeResult")
            if (descriptor.uuid == Constants.CCCD_UUID) {
                emitEvent(GattClientEvent.NotificationStatus(gatt.device.address, descriptor.characteristic.uuid, value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE), false))
            }
        } else {
            Log.d(TAG, "writeDescriptor 요청 성공 (${gatt.device.address} / ${descriptor.uuid})")
        }
    }

    /**
     * GATT 객체와 UUID를 이용하여 Characteristic을 찾습니다.
     */
    private fun findCharacteristic(gatt: BluetoothGatt, serviceUuid: UUID, characteristicUuid: UUID): BluetoothGattCharacteristic? {
        val service = gatt.getService(serviceUuid) ?: run {
            val errorMsg = "서비스 $serviceUuid 를 찾을 수 없습니다. (${gatt.device.address})"
            Log.w(TAG, errorMsg)
            emitEvent(GattClientEvent.Error(gatt.device.address, errorMsg))
            return null
        }
        return service.getCharacteristic(characteristicUuid) ?: run {
            val errorMsg = "Characteristic $characteristicUuid 를 찾을 수 없습니다. (서비스: $serviceUuid, 기기: ${gatt.device.address})"
            Log.w(TAG, errorMsg)
            emitEvent(GattClientEvent.Error(gatt.device.address, errorMsg))
            return null
        }
    }

    // --- GATT 콜백 구현 ---
    private val gattCallback = object : BluetoothGattCallback() {

        // 연결 상태 변경 시 호출
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            gatt ?: return
            val deviceAddress = gatt.device.address
            val deviceName = getDeviceName(gatt.device)

            handler.removeCallbacksAndMessages(deviceAddress) // 타임아웃 핸들러 제거
            connectingDevices.remove(deviceAddress) // 연결 시도 목록에서 제거

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.i(TAG, "연결 성공: $deviceName ($deviceAddress)")
                        activeGattConnections[deviceAddress] = gatt
                        emitEvent(GattClientEvent.ConnectionChange(deviceAddress, ConnectionState.CONNECTED))
                        // 서비스 탐색 시작
                        handler.postDelayed({ discoverServices(gatt) }, 600)
                    } else {
                        Log.w(TAG, "연결 실패 (status=$status): $deviceName ($deviceAddress)")
                        emitEvent(GattClientEvent.ConnectionChange(deviceAddress, ConnectionState.FAILED))
                        emitEvent(GattClientEvent.Error(deviceAddress, "연결 실패", status))
                        closeGatt(gatt)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "연결 해제됨 (status=$status): $deviceName ($deviceAddress)")
                    emitEvent(GattClientEvent.ConnectionChange(deviceAddress, ConnectionState.DISCONNECTED))
                    closeGatt(gatt)
                }
                BluetoothProfile.STATE_CONNECTING -> Log.d(TAG, "연결 중... ($deviceAddress)")
                BluetoothProfile.STATE_DISCONNECTING -> Log.d(TAG, "연결 해제 중... ($deviceAddress)")
            }
        }

        // 서비스 탐색 완료 시 호출
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            gatt ?: return
            val deviceAddress = gatt.device.address
            val deviceName = getDeviceName(gatt.device)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "서비스 발견 성공: $deviceName ($deviceAddress)")
                emitEvent(GattClientEvent.ServicesDiscovered(deviceAddress))
                // 메시징 Characteristic 알림 활성화 시도
                scope.launch {
                    try {
                        delay(200) // 안정화 시간
                        Log.d(TAG,"onServicesDiscovered: $deviceAddress 에서 알림 활성화 시도...")
                        enableNotifications(deviceAddress, Constants.MESH_SERVICE_UUID.uuid, Constants.MESH_CHARACTERISTIC_UUID)
                    } catch (e: Exception) {
                        Log.w(TAG, "onServicesDiscovered 내 알림 활성화 코루틴 오류: ${e.message}")
                    }
                }
            } else {
                Log.w(TAG, "서비스 발견 실패 (status=$status): $deviceName ($deviceAddress)")
                emitEvent(GattClientEvent.Error(deviceAddress, "서비스 발견 실패", status))
                disconnect(deviceAddress)
            }
        }

        // Characteristic 값 변경(알림 수신) 시 호출 (Android 13 이상)
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleCharacteristicChange(gatt.device.address, characteristic, value)
        }

        // Characteristic 값 변경(알림 수신) 시 호출 (Android 12 이하 호환성)
        @Deprecated("Use onCharacteristicChanged(gatt, characteristic, value) instead")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION") // 이전 버전 API 사용 경고 무시
                handleCharacteristicChange(gatt.device.address, characteristic, characteristic.value ?: byteArrayOf())
            }
        }

        // Characteristic 쓰기 완료 시 호출 (WRITE_TYPE_DEFAULT 사용 시)
        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            gatt ?: return; characteristic ?: return
            val deviceAddress = gatt.device.address
            val success = status == BluetoothGatt.GATT_SUCCESS

            if (success) {
                Log.d(TAG, "쓰기 성공 콜백: $deviceAddress / ${characteristic.uuid}")
            } else {
                Log.w(TAG, "쓰기 실패 콜백 (status=$status): $deviceAddress / ${characteristic.uuid}")
            }
            emitEvent(GattClientEvent.WriteResult(deviceAddress, characteristic.uuid, success))
        }

        // Descriptor 쓰기 완료 시 호출 (주로 CCCD 설정 결과)
        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            gatt ?: return; descriptor ?: return
            val deviceAddress = gatt.device.address
            val characteristicUuid = descriptor.characteristic.uuid
            val success = status == BluetoothGatt.GATT_SUCCESS

            if (descriptor.uuid == Constants.CCCD_UUID) {
                // CCCD 쓰기 완료 결과 처리
                val enabled = descriptor.value?.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ?: false
                if (success) {
                    Log.i(TAG, "CCCD 쓰기 성공 콜백 ($deviceAddress / $characteristicUuid): 알림 ${if(enabled)"활성화" else "비활성화"} 완료")
                } else {
                    Log.w(TAG, "CCCD 쓰기 실패 콜백 (status=$status): $deviceAddress / $characteristicUuid")
                }
                emitEvent(GattClientEvent.NotificationStatus(deviceAddress, characteristicUuid, enabled, success))
            } else {
                // 다른 Descriptor 쓰기 완료 처리
                Log.d(TAG, "기타 Descriptor 쓰기 완료 콜백 ($deviceAddress / ${descriptor.uuid}), status=$status")
            }
        }

        // TODO: 필요한 다른 콜백 구현 (onCharacteristicRead, onDescriptorRead, onMtuChanged 등)

    } // End of gattCallback

    // --- 내부 헬퍼 함수 ---

    /** Characteristic 변경(알림 수신) 공통 처리 로직 */
    private fun handleCharacteristicChange(address: String, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (characteristic.uuid == Constants.MESH_CHARACTERISTIC_UUID) {
            Log.d(TAG, "메시지 수신 (알림) from $address: ${value.size} bytes")
            Message.fromByteArray(value)?.let { message ->
                emitEvent(GattClientEvent.MessageReceived(address, message))
            } ?: run {
                Log.w(TAG, "메시지 파싱 실패 from $address: ${value.toString(Charsets.UTF_8)}")
                emitEvent(GattClientEvent.Error(address, "수신 메시지 파싱 실패"))
            }
        } else {
            Log.d(TAG, "알 수 없는 Characteristic 변경 ($address / ${characteristic.uuid})")
        }
    }

    /** GATT 연결 리소스를 정리하고 맵에서 제거합니다. */
    private fun closeGatt(gatt: BluetoothGatt?) {
        gatt ?: return
        val address = gatt.device.address
        Log.d(TAG, "GATT 연결 닫기 시작: $address")
        activeGattConnections.remove(address)
        connectingDevices.remove(address)
        handler.removeCallbacksAndMessages(address) // 타임아웃 핸들러 제거

        if (hasConnectPermission()) {
            try { gatt.close() } catch (e: Exception) { Log.e(TAG, "Gatt close 중 예외 발생 ($address): ${e.message}") }
        } else { Log.w(TAG, "Gatt close 불가 ($address): BLUETOOTH_CONNECT 권한 없음") }
        Log.i(TAG, "GATT 연결 닫기 완료: $address")
    }

    /** 이벤트를 SharedFlow로 발행합니다. */
    private fun emitEvent(event: GattClientEvent) {
        val result = _gattEvents.tryEmit(event)
        if (!result) Log.w(TAG, "GattClientEvent 발행 실패 (버퍼 가득 참?): $event")
    }

    /** 기기 이름을 안전하게 가져옵니다. */
    private fun getDeviceName(device: BluetoothDevice): String {
        try { return device.name ?: "Unknown" } catch (e: SecurityException) { return "Unknown (No Permission)" }
    }

    /** BLUETOOTH_CONNECT 권한 확인 */
    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else { true }
    }

    /** 현재 연결 시도 중인지 확인 */
    fun isConnecting(address: String): Boolean = connectingDevices.contains(address)

    /** 현재 활성 연결된 GATT 객체 목록 반환 */
    fun getActiveConnections(): Map<String, BluetoothGatt> = activeGattConnections.toMap()

    /** 주소로 BluetoothDevice 객체 반환 */
    fun getBluetoothDevice(address: String): BluetoothDevice? = activeGattConnections[address]?.device

    /** Manager 종료 시 자원 정리 */
    fun cleanup() {
        Log.d(TAG, "GattClientManager 정리 시작")
        disconnectAll() // 모든 연결 해제
        scope.cancel() // 내부 코루틴 스코프 취소
        handler.removeCallbacksAndMessages(null) // 모든 핸들러 메시지 제거
        connectingDevices.clear()
        Log.d(TAG, "GattClientManager 정리 완료")
    }
} // End of GattClientManager class