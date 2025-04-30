package com.example.blemodule.data.source.remote

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.* // Bluetooth 관련 클래스 임포트
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log // Log 임포트
import androidx.core.content.ContextCompat
import com.example.blemodule.data.event.GattServerEvent // 수정: event 패키지 사용
import com.example.blemodule.data.model.ConnectionState
import com.example.blemodule.data.model.Message
import com.example.blemodule.util.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow // BufferOverflow 임포트
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * GATT 서버 역할을 수행하여 클라이언트의 연결 요청을 받고 데이터를 교환하는 클래스입니다.
 * @param context 애플리케이션 컨텍스트
 * @param externalScope 외부(Service 등)에서 관리하는 코루틴 스코프
 */
@SuppressLint("MissingPermission") // 권한 확인은 각 함수 내부에서 수행
class GattServerManager(
    private val context: Context,
    private val externalScope: CoroutineScope // 외부 스코프 사용
) {
    private val TAG = "GattServerManager"
    // 시스템 Bluetooth 서비스 접근 매니저
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    // Bluetooth 어댑터 (기기의 블루투스 하드웨어 제어)
    private val bluetoothAdapter = bluetoothManager?.adapter

    // GATT 서버 객체 (Nullable)
    private var gattServer: BluetoothGattServer? = null
    // GATT 서비스 객체 (메시징용)
    private var meshService: BluetoothGattService? = null
    // GATT Characteristic 객체 (메시징용)
    private var meshCharacteristic: BluetoothGattCharacteristic? = null

    // 연결된 클라이언트 기기 관리 (Key: MAC 주소, Value: BluetoothDevice 객체)
    private val connectedClients = ConcurrentHashMap<String, BluetoothDevice>()

    // GATT 서버 이벤트를 외부(Repository 등)로 전달하기 위한 SharedFlow
    private val _gattServerEvents = MutableSharedFlow<GattServerEvent>(
        replay = 0, extraBufferCapacity = 10, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val gattServerEvents: SharedFlow<GattServerEvent> = _gattServerEvents.asSharedFlow()

    // 서버 시작 상태 플래그
    private var isServerStarted = false

    // GATT 서버 콜백 구현
    private val gattServerCallback = object : BluetoothGattServerCallback() {

        // 클라이언트 연결 상태 변경 시 호출
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            device ?: return // device가 null 이면 처리 불가
            val deviceAddress = device.address
            val deviceName = getDeviceName(device)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "클라이언트 연결됨: $deviceName ($deviceAddress)")
                    connectedClients[deviceAddress] = device
                    emitEvent(GattServerEvent.ConnectionChange(deviceAddress, device, ConnectionState.CONNECTED))
                    // TODO: 연결 성공 후 클라이언트에게 초기 정보 전송 등의 로직 추가 가능
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "클라이언트 연결 해제됨: $deviceName ($deviceAddress)")
                    connectedClients.remove(deviceAddress)
                    emitEvent(GattServerEvent.ConnectionChange(deviceAddress, device, ConnectionState.DISCONNECTED))
                }
            } else {
                // 연결 실패 또는 비정상적 연결 해제
                Log.w(TAG, "클라이언트 연결 상태 변경 오류 (status=$status, newState=$newState): $deviceName ($deviceAddress)")
                connectedClients.remove(deviceAddress) // 맵에서 제거
                // 상태는 DISCONNECTED 또는 FAILED 로 보고할 수 있음
                emitEvent(GattServerEvent.ConnectionChange(deviceAddress, device, if(newState == BluetoothProfile.STATE_DISCONNECTED) ConnectionState.DISCONNECTED else ConnectionState.FAILED))
                emitEvent(GattServerEvent.Error("클라이언트 연결 오류", IllegalStateException("Status: $status")))
            }
        }

        // 서비스 추가 완료 시 호출
        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "GATT 서비스 추가 성공: ${service?.uuid}")
            } else {
                Log.e(TAG, "GATT 서비스 추가 실패: status=$status")
                emitEvent(GattServerEvent.Error("GATT 서비스 추가 실패", IllegalStateException("Status: $status")))
                stopServerInternal() // 실패 시 서버 정리
            }
        }

        // 클라이언트로부터 Characteristic 읽기 요청 시 호출
        override fun onCharacteristicReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic?) {
            device ?: return; characteristic ?: return
            if (!hasConnectPermission()) {
                Log.w(TAG, "읽기 요청 응답 불가 (${device.address}): 권한 없음")
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION, offset, null)
                return
            }
            if (characteristic.uuid == Constants.MESH_CHARACTERISTIC_UUID) {
                Log.d(TAG, "읽기 요청 받음 from ${device.address} for ${characteristic.uuid}")
                // TODO: 현재 상태 또는 특정 정보 반환 로직 구현
                val responseValue = "Server OK".toByteArray(Charsets.UTF_8)
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, responseValue)
            } else {
                Log.w(TAG, "알 수 없는 Characteristic 읽기 요청: ${characteristic.uuid}")
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
            }
        }

        // 클라이언트로부터 Characteristic 쓰기 요청 시 호출
        override fun onCharacteristicWriteRequest(device: BluetoothDevice?, requestId: Int, characteristic: BluetoothGattCharacteristic?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            device ?: return; characteristic ?: return; value ?: return
            val deviceAddress = device.address
            if (!hasConnectPermission()) {
                Log.w(TAG, "쓰기 요청 응답 불가 ($deviceAddress): 권한 없음")
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION, offset, null)
                }
                return
            }
            if (characteristic.uuid == Constants.MESH_CHARACTERISTIC_UUID) {
                Log.d(TAG, "쓰기 요청 받음 from $deviceAddress for ${characteristic.uuid}: ${value.size} bytes")
                // 수신된 데이터를 Message 객체로 파싱
                Message.fromByteArray(value)?.let { message ->
                    emitEvent(GattServerEvent.MessageReceived(deviceAddress, message))
                    // 성공 응답 전송 (필요한 경우)
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                    }
                } ?: run {
                    Log.w(TAG, "메시지 파싱 실패 from $deviceAddress: ${value.toString(Charsets.UTF_8)}")
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                    }
                    emitEvent(GattServerEvent.Error("수신 메시지 파싱 실패", IllegalArgumentException("Invalid message format")))
                }
            } else {
                Log.w(TAG, "알 수 없는 Characteristic 쓰기 요청: ${characteristic.uuid}")
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, null)
                }
            }
        }

        // 클라이언트로부터 Descriptor 읽기 요청 시 호출
        override fun onDescriptorReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor?) {
            device ?: return; descriptor ?: return
            if (!hasConnectPermission()) {
                Log.w(TAG, "Descriptor 읽기 요청 응답 불가 (${device.address}): 권한 없음")
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION, offset, null)
                return
            }
            if (descriptor.uuid == Constants.CCCD_UUID) {
                Log.d(TAG, "CCCD 읽기 요청 받음 from ${device.address}")
                // TODO: 클라이언트별 구독 상태 반환 로직 필요
                val responseValue = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE // 임시 기본값
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, responseValue)
            } else {
                Log.w(TAG, "알 수 없는 Descriptor 읽기 요청: ${descriptor.uuid}")
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
            }
        }

        // 클라이언트로부터 Descriptor 쓰기 요청 시 호출
        override fun onDescriptorWriteRequest(device: BluetoothDevice?, requestId: Int, descriptor: BluetoothGattDescriptor?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            device ?: return; descriptor ?: return; value ?: return
            val deviceAddress = device.address
            if (!hasConnectPermission()) {
                Log.w(TAG, "Descriptor 쓰기 요청 응답 불가 ($deviceAddress): 권한 없음")
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION, offset, null)
                }
                return
            }
            if (descriptor.uuid == Constants.CCCD_UUID) {
                var status = BluetoothGatt.GATT_FAILURE
                if (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    Log.i(TAG, "클라이언트 알림 구독 시작: $deviceAddress / ${descriptor.characteristic.uuid}")
                    // TODO: 클라이언트별 구독 상태 저장/관리
                    status = BluetoothGatt.GATT_SUCCESS
                    emitEvent(GattServerEvent.ClientSubscribed(deviceAddress, descriptor.characteristic.uuid))
                } else if (value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                    Log.i(TAG, "클라이언트 알림 구독 해제: $deviceAddress / ${descriptor.characteristic.uuid}")
                    // TODO: 클라이언트별 구독 상태 제거/관리
                    status = BluetoothGatt.GATT_SUCCESS
                    emitEvent(GattServerEvent.ClientUnsubscribed(deviceAddress, descriptor.characteristic.uuid))
                } else {
                    Log.w(TAG, "잘못된 CCCD 쓰기 값 수신 from $deviceAddress: ${value.contentToString()}")
                    status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
                }
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, status, offset, value)
                }
            } else {
                Log.w(TAG, "알 수 없는 Descriptor 쓰기 요청: ${descriptor.uuid}")
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, null)
                }
            }
        }

        // 클라이언트에게 알림 전송 완료 시 호출
        override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
            device ?: return
            val success = status == BluetoothGatt.GATT_SUCCESS
            if (success) {
                Log.d(TAG, "알림 전송 성공 to ${device.address}")
            } else {
                Log.w(TAG, "알림 전송 실패 to ${device.address}, status=$status")
            }
            emitEvent(GattServerEvent.NotificationSent(device.address, success))
        }

        // TODO: onExecuteWrite 등 필요한 다른 콜백 구현
    } // End of gattServerCallback

    /**
     * GATT 서버를 시작하고 서비스를 등록합니다.
     * @throws IllegalStateException 서버 초기화 실패 시
     * @throws SecurityException 필요한 권한 부족 시
     */
    suspend fun startServer() {
        if (isServerStarted) {
            Log.w(TAG, "GATT 서버가 이미 시작되었습니다.")
            return
        }
        // 필요한 권한 확인
        if (!hasConnectPermission() || !hasAdvertisePermission()) {
            val missingPerms = mutableListOf<String>()
            if (!hasConnectPermission()) missingPerms.add("BLUETOOTH_CONNECT")
            if (!hasAdvertisePermission()) missingPerms.add("BLUETOOTH_ADVERTISE")
            val errorMsg = "GATT 서버 시작 불가: 권한 부족 - ${missingPerms.joinToString()}"
            Log.e(TAG, errorMsg)
            emitEvent(GattServerEvent.Error(errorMsg))
            throw SecurityException(errorMsg)
        }
        // Bluetooth 어댑터 확인
        if (bluetoothAdapter == null) {
            val errorMsg = "GATT 서버 시작 불가: Bluetooth 어댑터 없음"
            Log.e(TAG, errorMsg)
            emitEvent(GattServerEvent.Error(errorMsg))
            throw IllegalStateException(errorMsg)
        }
        // isEnabled 접근 전에 SuppressLint 추가
        @SuppressLint("MissingPermission")
        if (!bluetoothAdapter.isEnabled) {
            val errorMsg = "GATT 서버 시작 불가: 블루투스 비활성화됨"
            Log.e(TAG, errorMsg)
            emitEvent(GattServerEvent.Error(errorMsg))
            throw IllegalStateException(errorMsg)
        }

        Log.d(TAG, "GATT 서버 열기 시도...")
        // GATT 서버 열기
        gattServer = bluetoothManager?.openGattServer(context, gattServerCallback) ?: run {
            val errorMsg = "GATT 서버 열기 실패 (openGattServer 반환값 null)"
            Log.e(TAG, errorMsg)
            emitEvent(GattServerEvent.Error(errorMsg))
            throw IllegalStateException(errorMsg)
        }
        Log.i(TAG, "GATT 서버가 성공적으로 열렸습니다.")

        // GATT 서비스 및 Characteristic 설정
        setupGattService()

        isServerStarted = true
        Log.i(TAG, "GATT 서버 시작 완료.")
    }

    /** GATT 서비스 및 Characteristic 을 생성하고 서버에 추가합니다. */
    private suspend fun setupGattService() {
        // MESH 서비스 생성
        meshService = BluetoothGattService(Constants.MESH_SERVICE_UUID.uuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // MESH Characteristic 생성
        meshCharacteristic = BluetoothGattCharacteristic(
            Constants.MESH_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
        ).apply {
            // CCCD 추가
            addDescriptor(
                BluetoothGattDescriptor(
                    Constants.CCCD_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                )
            )
        }

        // Characteristic 을 서비스에 추가
        if (meshService?.addCharacteristic(meshCharacteristic) == false) {
            val errorMsg = "GATT Characteristic 추가 실패"
            Log.e(TAG, errorMsg)
            stopServerInternal()
            emitEvent(GattServerEvent.Error(errorMsg))
            throw IllegalStateException(errorMsg)
        }

        // 서비스를 GATT 서버에 추가
        Log.d(TAG, "GATT 서비스 추가 시도...")
        if (gattServer?.addService(meshService) == false) {
            val errorMsg = "GATT 서비스 추가 요청 실패"
            Log.e(TAG, errorMsg)
            stopServerInternal()
            emitEvent(GattServerEvent.Error(errorMsg))
            throw IllegalStateException(errorMsg)
        }
        Log.d(TAG, "GATT 서비스 추가 요청 완료. (결과는 onServiceAdded 콜백)")
    }

    /** GATT 서버를 중지하고 리소스를 정리합니다. */
    fun stopServer() {
        stopServerInternal()
    }

    /** 내부 서버 중지 및 정리 로직 */
    private fun stopServerInternal() {
        if (!isServerStarted) {
            Log.d(TAG, "GATT 서버가 이미 중지되었거나 시작되지 않았습니다.")
            return
        }
        Log.i(TAG, "GATT 서버 중지 시작...")
        if (!hasConnectPermission()) {
            Log.w(TAG, "GATT 서버 닫기 불가: BLUETOOTH_CONNECT 권한 없음")
            // 상태는 정리
            isServerStarted = false
            gattServer = null
            meshService = null
            meshCharacteristic = null
            connectedClients.clear()
            return
        }
        try {
            disconnectAllClients() // 모든 클라이언트 연결 해제
            gattServer?.close() // GATT 서버 닫기
            Log.i(TAG, "GATT 서버가 닫혔습니다.")
        } catch (e: Exception) {
            Log.e(TAG, "GATT 서버 닫기 중 예외 발생: ${e.message}")
            emitEvent(GattServerEvent.Error("GATT 서버 닫기 오류", e))
        } finally {
            // 상태 변수 및 객체 참조 정리
            isServerStarted = false
            gattServer = null
            meshService = null
            meshCharacteristic = null
            connectedClients.clear()
            Log.i(TAG, "GATT 서버 리소스 정리 완료.")
        }
    }

    /** 연결된 특정 클라이언트에게 데이터를 Notification 으로 전송합니다. */
    fun notifyCharacteristicChanged(address: String, serviceUuid: UUID, characteristicUuid: UUID, data: ByteArray): Boolean {
        if (!isServerStarted) {
            Log.w(TAG, "알림 전송 불가 ($address): 서버 미시작")
            return false
        }
        if (!hasConnectPermission()) {
            Log.w(TAG, "알림 전송 불가 ($address): BLUETOOTH_CONNECT 권한 없음")
            emitEvent(GattServerEvent.Error("알림 전송 권한 없음"))
            return false
        }
        // 해당 서비스/Characteristic 인지 확인
        if (serviceUuid != Constants.MESH_SERVICE_UUID.uuid || characteristicUuid != Constants.MESH_CHARACTERISTIC_UUID) {
            Log.w(TAG, "알림 전송 불가 ($address): 지원되지 않는 UUID ($serviceUuid / $characteristicUuid)")
            return false
        }
        val device = connectedClients[address] ?: run {
            Log.w(TAG, "알림 전송 불가 ($address): 연결된 클라이언트 아님")
            return false
        }
        meshCharacteristic?.let { char ->
            // 알림 속성 확인
            if ((char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
                Log.w(TAG, "알림 전송 불가 ($address): Characteristic ${char.uuid} 알림 속성 없음")
                return false
            }

            Log.d(TAG, "클라이언트에게 알림 전송 시도 -> $address (${data.size} bytes)")
            val notifyResult: Int // 상태 코드 저장 변수
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13 (API 33) 이상
                notifyResult = gattServer?.notifyCharacteristicChanged(device, char, false, data) ?: BluetoothStatusCodes.ERROR_UNKNOWN
            } else {
                // Android 12 (API 32) 이하
                @Suppress("DEPRECATION")
                char.value = data // 값 설정
                @Suppress("DEPRECATION")
                val success = gattServer?.notifyCharacteristicChanged(device, char, false) ?: false
                notifyResult = if (success) BluetoothStatusCodes.SUCCESS else BluetoothStatusCodes.ERROR_UNKNOWN
            }

            // 알림 요청 결과 확인
            if (notifyResult != BluetoothStatusCodes.SUCCESS) {
                Log.e(TAG, "notifyCharacteristicChanged 요청 실패 ($address), status=$notifyResult")
                emitEvent(GattServerEvent.NotificationSent(address, false))
                return false
            }
            // 실제 전송 완료는 onNotificationSent 콜백에서 확인
            return true // 요청 성공
        } ?: run {
            Log.e(TAG, "알림 전송 불가 ($address): meshCharacteristic 이 null 입니다.")
            return false
        }
    }

    /** 특정 클라이언트의 연결을 해제합니다. */
    fun disconnectClient(address: String) {
        if (!hasConnectPermission()) {
            Log.w(TAG, "클라이언트 연결 해제 불가 ($address): 권한 없음")
            return
        }
        connectedClients[address]?.let { device ->
            Log.i(TAG, "클라이언트 연결 해제 요청 -> $address")
            gattServer?.cancelConnection(device)
        } ?: Log.w(TAG, "연결 해제 요청: 해당 주소의 클라이언트 없음 ($address)")
    }

    /** 연결된 모든 클라이언트의 연결을 해제합니다. */
    fun disconnectAllClients() {
        if (!hasConnectPermission()) {
            Log.w(TAG, "모든 클라이언트 연결 해제 불가: 권한 없음")
            return
        }
        Log.i(TAG, "모든 클라이언트 연결 해제 요청...")
        connectedClients.values.toList().forEach { device ->
            try { gattServer?.cancelConnection(device) } catch (e: Exception) { Log.e(TAG, "클라이언트($device.address) 연결 해제 중 오류: ${e.message}")}
        }
        // 콜백에서 connectedClients 맵이 정리됨
    }

    /** 현재 연결된 클라이언트 주소 목록을 반환합니다. */
    fun getConnectedDevices(): Set<String> = connectedClients.keys.toSet()

    /** 주소로 연결된 클라이언트의 BluetoothDevice 객체를 반환합니다. */
    fun getBluetoothDevice(address: String): BluetoothDevice? = connectedClients[address]

    /** 이벤트를 SharedFlow로 발행합니다. */
    private fun emitEvent(event: GattServerEvent) {
        val result = _gattServerEvents.tryEmit(event)
        if (!result) { Log.w(TAG, "GattServerEvent 발행 실패 (버퍼 가득 참?): $event") }
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
    /** BLUETOOTH_ADVERTISE 권한 확인 */
    private fun hasAdvertisePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        } else { true }
    }

    /** Manager 종료 시 자원 정리 */
    fun cleanup() {
        Log.d(TAG, "GattServerManager 정리 시작")
        stopServerInternal() // 서버 중지 및 정리
        // SharedFlow 관련 리소스는 외부 스코프에 의해 관리됨
        Log.d(TAG, "GattServerManager 정리 완료")
    }
}