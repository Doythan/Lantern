package com.example.blemodule.data.repository

// Android Framework Imports
import android.Manifest // Manifest 추가 (PermissionHelper 와는 별개로 SuppressLint 용)
import android.annotation.SuppressLint // SuppressLint 임포트 추가
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Looper
import android.util.Log
// Data Layer Imports (Model, State, Event)
import com.example.blemodule.data.event.GattClientEvent
import com.example.blemodule.data.event.GattServerEvent
import com.example.blemodule.data.model.BleDevice
import com.example.blemodule.data.model.ConnectionState
import com.example.blemodule.data.model.Message
import com.example.blemodule.data.source.remote.BleAdvertiserManager
import com.example.blemodule.data.source.remote.BleScannerManager
import com.example.blemodule.data.source.remote.GattClientManager
import com.example.blemodule.data.source.remote.GattServerManager
import com.example.blemodule.data.state.AdvertisingState
import com.example.blemodule.data.state.ScanState
// Util Layer Imports
import com.example.blemodule.util.BluetoothUtils
import com.example.blemodule.util.Constants
// Kotlin Coroutines Imports
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
// Java Utility Imports
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * BLE 관련 데이터 및 상태를 중앙에서 관리하는 저장소 클래스입니다.
 * 스캔, 광고, GATT 클라이언트/서버 매니저를 통합하고 상태를 외부에 Flow 형태로 제공합니다.
 * @param context 애플리케이션 컨텍스트
 * @param externalScope 애플리케이션 레벨의 코루틴 스코프 (Service 등에서 관리)
 */
class BleRepository(
    private val context: Context,
    private val externalScope: CoroutineScope // 외부(Service 등)에서 관리하는 스코프 사용
) {
    private val TAG = "BleRepository"

    // --- BLE 매니저 인스턴스 ---
    private val bluetoothAdapter = BluetoothUtils.getBluetoothAdapter(context)
    private val scannerManager = BleScannerManager(context, bluetoothAdapter)
    private val advertiserManager = BleAdvertiserManager(context, bluetoothAdapter)
    private val gattClientManager = GattClientManager(context)
    private val gattServerManager = GattServerManager(context, externalScope)

    private val recentMsgIds = ArrayDeque<String>()   // 최근 100개 보관
    private val MAX_CACHE = 100

    // --- 상태 관리 Flow ---
    private var myDeviceId: String? = null
    private val _scannedDevices = MutableStateFlow<Map<String, BleDevice>>(emptyMap())
    val scannedDevices: StateFlow<Map<String, BleDevice>> = _scannedDevices.asStateFlow()
    private val connectedDevicesMap = ConcurrentHashMap<String, BleDevice>()
    private val _connectedDevices = MutableStateFlow<Map<String, BleDevice>>(emptyMap())
    val connectedDevices: StateFlow<Map<String, BleDevice>> = _connectedDevices.asStateFlow()
    private val knownNetworkDevices = CopyOnWriteArraySet<String>()
    private val _knownDevicesFlow = MutableStateFlow<Set<String>>(emptySet())
    val knownDevicesFlow: StateFlow<Set<String>> = _knownDevicesFlow.asStateFlow()
    private val _receivedAppMessages = MutableSharedFlow<Message.AppMessage>(
        replay                = 1,               // 앱 재구동 후 최근 1개 재생
        extraBufferCapacity   = 64,              // 64개까지 버퍼
        onBufferOverflow      = BufferOverflow.DROP_OLDEST
    )
    val receivedAppMessages: SharedFlow<Message.AppMessage> = _receivedAppMessages.asSharedFlow()
    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()
    private val _advertisingState = MutableStateFlow<AdvertisingState>(AdvertisingState.Stopped)
    val advertisingState: StateFlow<AdvertisingState> = _advertisingState.asStateFlow()
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Stopped)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()
    private val _logMessages = MutableSharedFlow<String>(extraBufferCapacity = 50)
    val logMessages: SharedFlow<String> = _logMessages.asSharedFlow()

    // 정보 메시지 브로드캐스트 Debounce 관리
    private val debounceHandler = android.os.Handler(Looper.getMainLooper())
    private var infoBroadcastDebounceJob: Job? = null

    init {
        observeGattClientEvents()
        observeGattServerEvents()
    }

    // --- 공개 함수들 ---
    fun setMyDeviceId(deviceId: String): Boolean {
        val id = deviceId.trim()
        // 길이·공백
        if (id.isBlank() || id.length > Constants.MAX_NICKNAME_LENGTH) {
            log("닉네임 길이 오류")
            return false
        }
        // 중복
        if (knownNetworkDevices.contains(id)) {
            log("닉네임 중복")
            return false
        }
        myDeviceId = id
        knownNetworkDevices.add(id)
        _knownDevicesFlow.value = knownNetworkDevices.toSet()
        log("내 닉네임 설정: $id")
        return true
    }
    fun startScanning() { /* 이전과 동일 */
        if (_isServiceActive.value && _scanState.value !is ScanState.Started) {
            log("BLE 스캔 시작 요청")
            _scannedDevices.value = emptyMap()
            externalScope.launch {
                scannerManager.startScanning()
                    .catch { e -> log("스캔 Flow 오류: ${e.message}") }
                    .collect { state ->
                        _scanState.value = state
                        when (state) {
                            is ScanState.DeviceFound -> {
                                val addr = state.device.address
                                // 이미 연결 중이거나 연결된 기기면 스킵
                                if (!gattClientManager.isConnecting(addr)
                                    && !connectedDevicesMap.containsKey(addr)
                                ) {
                                    _scannedDevices.update { it + (addr to state.device) }
                                    connectGatt(addr)
                                } else{
                                    log("이미 연결 중이거나 연결된 기기 스킵 → $addr")
                                }
                            }
                            is ScanState.Failed -> log("스캔 실패: ${state.message} (코드: ${state.errorCode})")
                            is ScanState.Started -> log("스캔 시작됨.")
                            is ScanState.Stopped -> log("스캔 중지됨.")
                        }
                    }
            }
        } else { log("스캔 시작 불가: 서비스 비활성(${_isServiceActive.value}) 또는 이미 시작됨(${_scanState.value})") }
    }
    fun stopScanning() { /* 이전과 동일 */
        if (_scanState.value is ScanState.Started) {
            log("BLE 스캔 중지 요청")
            scannerManager.stopScanning()
            _scanState.value = ScanState.Stopped
        }
    }
    fun startAdvertising() { /* 이전과 동일 */
        if (_isServiceActive.value && _advertisingState.value !is AdvertisingState.Started) {
            log("BLE 광고 시작 요청")
            externalScope.launch {
                advertiserManager.startAdvertising()
                    .catch { e -> log("광고 Flow 오류: ${e.message}") }
                    .collect { state ->
                        _advertisingState.value = state
                        when (state) {
                            is AdvertisingState.Started -> log("광고 시작됨.")
                            is AdvertisingState.Failed -> log("광고 시작 실패: ${state.message} (코드: ${state.errorCode})")
                            is AdvertisingState.Stopped -> log("광고 중지됨.")
                        }
                    }
            }
        } else { log("광고 시작 불가: 서비스 비활성(${_isServiceActive.value}) 또는 이미 시작됨(${_advertisingState.value})") }
    }
    fun stopAdvertising() { /* 이전과 동일 */
        if (_advertisingState.value is AdvertisingState.Started) {
            log("BLE 광고 중지 요청")
            advertiserManager.stopAdvertising()
            _advertisingState.value = AdvertisingState.Stopped
        }
    }
    fun startGattServer() { /* 이전과 동일 */
        if (_isServiceActive.value) {
            log("GATT 서버 시작 요청")
            externalScope.launch {
                try {
                    gattServerManager.startServer()
                    log("GATT 서버 시작 완료.")
                } catch (e: Exception) { log("GATT 서버 시작 실패: ${e.message}") }
            }
        } else { log("GATT 서버 시작 불가: 서비스 비활성") }
    }
    fun stopGattServer() { /* 이전과 동일 */
        log("GATT 서버 중지 요청")
        gattServerManager.stopServer()
    }
    fun connectGatt(address: String) { /* 이전과 동일 */
        val deviceToConnect = _scannedDevices.value[address]?.device ?: run { log("연결 시도 실패: 스캔 목록에 없는 주소 $address"); return }
        if (connectedDevicesMap.containsKey(address) || gattClientManager.isConnecting(address)) { log("이미 연결되었거나 연결 시도 중인 기기입니다: $address"); return }
        log("GATT 연결 시도 -> $address")
        gattClientManager.connect(deviceToConnect)
    }
    fun disconnectGatt(address: String) { /* 이전과 동일 */
        if (!connectedDevicesMap.containsKey(address)) { log("연결 해제 시도 실패: 연결되지 않은 기기 $address"); return }
        log("GATT 연결 해제 시도 -> $address")
        gattClientManager.disconnect(address)
        gattServerManager.disconnectClient(address)
    }
    fun disconnectAll() { /* 이전과 동일 */
        log("모든 GATT 연결 해제 요청")
        gattClientManager.disconnectAll()
        gattServerManager.disconnectAllClients()
    }
    fun sendMessage(message: Message, targetAddress: String? = null) { /* 이전과 동일 */
        if (!_isServiceActive.value) { log("메시지 전송 실패: 서비스 비활성"); return }
        val myId = myDeviceId ?: run { log("메시지 전송 실패: 내 ID 미설정"); return }
        val bytesToSend = message.toByteArray()
        externalScope.launch(Dispatchers.IO) {
            log("메시지 전송/릴레이 시작: ${message::class.java.simpleName} to ${targetAddress ?: "ALL"}")
            gattClientManager.getActiveConnections().forEach { (address, _) ->
                if (targetAddress == null || targetAddress == address) {
                    log(" -> 클라이언트->서버 ($address) 전송 시도")
                    gattClientManager.writeCharacteristic(address, Constants.MESH_SERVICE_UUID.uuid, Constants.MESH_CHARACTERISTIC_UUID, bytesToSend)
                    delay(50)
                }
            }
            gattServerManager.getConnectedDevices().forEach { address ->
                if (targetAddress == null || targetAddress == address) {
                    log(" -> 서버->클라이언트 ($address) 알림 시도")
                    gattServerManager.notifyCharacteristicChanged(address, Constants.MESH_SERVICE_UUID.uuid, Constants.MESH_CHARACTERISTIC_UUID, bytesToSend)
                    delay(50)
                }
            }
            log("메시지 전송/릴레이 완료: ${message::class.java.simpleName}")
        }
    }
    fun cleanup() { /* 이전과 동일 */
        log("Repository 정리 시작")
        setServiceActive(false)
    }
    fun setServiceActive(isActive: Boolean) { /* 이전과 동일 */
        if (_isServiceActive.value == isActive) return
        _isServiceActive.value = isActive
        log("서비스 활성 상태 변경됨: $isActive")
        if (isActive) {
            myDeviceId?.let { knownNetworkDevices.add(it) }
            _knownDevicesFlow.value = knownNetworkDevices.toSet()
        } else {
            stopScanning()
            stopAdvertising()
            disconnectAll()
            stopGattServer()
            infoBroadcastDebounceJob?.cancel()
            _scannedDevices.value = emptyMap()
            connectedDevicesMap.clear()
            _connectedDevices.value = emptyMap()
            knownNetworkDevices.clear()
            _knownDevicesFlow.value = emptySet()
            log("Repository 내부 정리 완료 (서비스 비활성화)")
        }
    }

    // --- 이벤트 관찰 및 처리 ---
    private fun observeGattClientEvents() { /* 이전과 동일 */
        gattClientManager.gattEvents
            .onEach { event ->
                when (event) {
                    is GattClientEvent.ConnectionChange -> handleConnectionChange(event.address, event.state, isClient = true)
                    is GattClientEvent.MessageReceived -> handleReceivedMessage(event.address, event.message)
                    is GattClientEvent.ServicesDiscovered -> log("클라이언트: 서비스 발견 완료 - ${event.address}")
                    is GattClientEvent.NotificationStatus -> log("클라이언트: 알림 상태 변경 - ${event.address}, Char: ${event.characteristicUuid}, 활성: ${event.enabled}, 성공: ${event.success}")
                    is GattClientEvent.WriteResult -> log("클라이언트: 쓰기 결과 - ${event.address}, Char: ${event.characteristicUuid}, 성공: ${event.success}")
                    is GattClientEvent.Error -> log("클라이언트 오류 (${event.address ?: "N/A"}): ${event.message} (코드: ${event.errorCode})")
                }
            }
            .catch { e -> log("GattClient 이벤트 구독 오류: ${e.message}") }
            .launchIn(externalScope)
    }
    private fun observeGattServerEvents() { /* 이전과 동일 */
        gattServerManager.gattServerEvents
            .onEach { event ->
                when (event) {
                    is GattServerEvent.ConnectionChange -> handleConnectionChange(event.address, event.state, isClient = false, device = event.device)
                    is GattServerEvent.MessageReceived -> handleReceivedMessage(event.address, event.message)
                    is GattServerEvent.NotificationSent -> log("서버: 알림 전송 완료 - ${event.address}, 성공: ${event.success}")
                    is GattServerEvent.ClientSubscribed -> log("서버: 클라이언트 구독 시작 - ${event.address}, Char: ${event.characteristicUuid}")
                    is GattServerEvent.ClientUnsubscribed -> log("서버: 클라이언트 구독 해제 - ${event.address}, Char: ${event.characteristicUuid}")
                    is GattServerEvent.Error -> log("서버 오류: ${event.message} ${event.throwable?.localizedMessage ?: ""}")
                }
            }
            .catch { e -> log("GattServer 이벤트 구독 오류: ${e.message}") }
            .launchIn(externalScope)
    }
    private fun handleConnectionChange(address: String, newState: ConnectionState, isClient: Boolean, device: BluetoothDevice? = null) { /* 이전과 동일 */
        val bleDevice = getOrCreateBleDevice(address, isClient, device) ?: run {
            log("연결 상태 변경 처리 실패: 기기 정보 없음 ($address)")
            connectedDevicesMap.remove(address)
            _connectedDevices.value = connectedDevicesMap.toMap()
            return
        }
        val source = if (isClient) "클라이언트" else "서버"
        log("연결 상태 변경: ${bleDevice.getDeviceNameSafe()} ($address) -> $newState ($source)")
        if (newState == ConnectionState.CONNECTED) {
            bleDevice.connectionState = newState
            connectedDevicesMap[address] = bleDevice
            exchangeDeviceInfoOnConnect(address)
        } else {
            connectedDevicesMap.remove(address)?.let { it.connectionState = newState }
        }
        _connectedDevices.value = connectedDevicesMap.toMap()
    }
    private fun handleReceivedMessage(senderAddress: String, message: Message) { /* 이전과 동일 */
        log("메시지 수신 from $senderAddress: ${message::class.java.simpleName}")
        when (message) {
            is Message.DeviceInfo -> {
                val updated = knownNetworkDevices.addAll(message.knownDevices + message.sourceId)
                if (updated) {
                    log("새로운 기기 정보 수신/업데이트 from ${message.sourceId}: $knownNetworkDevices")
                    _knownDevicesFlow.value = knownNetworkDevices.toSet()
                    broadcastDeviceInfoWithDebounce()
                }
            }
            is Message.AppMessage -> {
                val msgId = message.rawData.hashCode().toString()

                if (recentMsgIds.contains(msgId)) return       // ★ 이미 본 메시지면 무시

                // 캐시 갱신
                recentMsgIds.addLast(msgId)
                if (recentMsgIds.size > MAX_CACHE) recentMsgIds.removeFirst()

                val myId = myDeviceId ?: return
                if (message.targetId == myId || message.targetId == Constants.BROADCAST_ID) {
                    _receivedAppMessages.tryEmit(message)
                } else {
                    sendMessage(message, null)  // 한 번만 릴레이
                }
            }
        }
    }
    private fun exchangeDeviceInfoOnConnect(address: String) { /* 이전과 동일 */
        val myId = myDeviceId ?: return
        externalScope.launch {
            delay(1000)
            log("연결 후 기기 정보 교환 시작 -> $address")
            val infoMessage = Message.createDeviceInfoMessage(myId, knownNetworkDevices.toSet())
            sendMessage(infoMessage, address)
        }
    }

    /** BleDevice 객체 가져오기 또는 생성 */
    private fun getOrCreateBleDevice(address: String, isClient: Boolean, device: BluetoothDevice?): BleDevice? {
        // 1. 현재 연결된 맵에서 찾기
        connectedDevicesMap[address]?.let { return it }
        // 2. 스캔된 맵에서 찾기
        _scannedDevices.value[address]?.let { return it }
        // 3. 매니저로부터 BluetoothDevice 객체 가져오기
        val bluetoothDevice = device // GattServer 이벤트에서 받은 객체 우선 사용
            ?: if (isClient) gattClientManager.getBluetoothDevice(address) else gattServerManager.getBluetoothDevice(address)

        // 4. BluetoothDevice 객체로 BleDevice 생성
        return bluetoothDevice?.let { btDevice ->
            // !! 수정: it.name 접근 전에 @SuppressLint 추가 !!
            @SuppressLint("MissingPermission")
            val name = try { btDevice.name } catch (e: SecurityException) { null } // 권한 예외 처리
            BleDevice(btDevice, name, address)
        }
    }

    /** 로그 메시지 발행 */
    private fun log(message: String) { /* 이전과 동일 */
        Log.d(TAG, message)
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val emitSuccessful = _logMessages.tryEmit("[$timestamp] $message")
        if (!emitSuccessful) println("$TAG Log buffer full: $message")
    }
    /** 네트워크 정보 브로드캐스트 (Debounce 적용) */
    private fun broadcastDeviceInfoWithDebounce() { /* 이전과 동일 */
        val myId = myDeviceId ?: return
        infoBroadcastDebounceJob?.cancel()
        infoBroadcastDebounceJob = externalScope.launch {
            delay(500)
            log("네트워크 정보 브로드캐스트 실행 (Debounced)")
            val currentKnownDevices = knownNetworkDevices.toSet()
            val infoMessage = Message.createDeviceInfoMessage(myId, currentKnownDevices)
            sendMessage(infoMessage, null)
        }
    }
} // End of BleRepository class