package com.ssafy.lanterns.data.source.ble.mesh

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ssafy.lanterns.data.source.ble.advertiser.AdvertiserManager
import com.ssafy.lanterns.data.source.ble.scanner.ScannerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.ssafy.lanterns.data.repository.MessageRepository
import kotlinx.coroutines.flow.firstOrNull

/**
 * 메시 채팅 매니저
 * 애플리케이션 계층 - 사용자 인터페이스와 메시 네트워크 계층 사이의 인터페이스 제공
 */
class MeshChatManager(
    private val context: Context,
    private val messageRepository: MessageRepository
) {
    private val TAG = "MeshChatManager"
    
    // 코루틴 스코프
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // 각 계층 컴포넌트
    private val advertiserManager by lazy { AdvertiserManager(context) }
    private val scannerManager by lazy { ScannerManager(context, this::handleMeshMessage, advertiserManager) }
    private val bleComm by lazy { BleCommImpl(context, advertiserManager, scannerManager) }
    private val transportLayer by lazy { TransportLayer() }
    private val securityManager by lazy { SecurityManager() }
    
    // 자신의 디바이스 정보
    private val ownAddress: Short = 0 // 초기 주소, 프로비저닝 후 변경됨
    private var deviceNickname: String = "Unknown"
    
    // 메시 네트워크 계층
    private val networkLayer: MeshNetworkLayer by lazy { 
        MeshNetworkLayer(bleComm, transportLayer, ownAddress).apply {
            // 메시지 수신 콜백 설정
            setOnMeshMessageCallback(object : MeshNetworkLayer.OnMeshMessageCallback {
                override fun onMessageReceived(
                    sourceAddress: Short,
                    messageType: MessageType,
                    payload: ByteArray,
                    isUrgent: Boolean
                ) {
                    handleReceivedMessage(sourceAddress, messageType, payload, isUrgent)
                }
            })
        }
    }
    
    // 프로비저닝 관리자
    private val provisioningManager: ProvisioningManager by lazy { 
        ProvisioningManager(context, scannerManager)
    }
    
    // 메시지 데이터
    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages
    
    // 주변 노드 데이터
    private val _nearbyNodes = MutableLiveData<List<NearbyNode>>(emptyList())
    val nearbyNodes: LiveData<List<NearbyNode>> = _nearbyNodes
    
    // 연결 상태
    private val _isNetworkActive = MutableLiveData(false)
    val isNetworkActive: LiveData<Boolean> = _isNetworkActive
    
    // 주변 노드 캐시
    private val nodeCache = ConcurrentHashMap<String, NearbyNode>()
    
    // 메시지 저장소
    private val messageStore = mutableListOf<ChatMessage>()
    
    // Wakelock (백그라운드 작업 유지)
    private var wakeLock: PowerManager.WakeLock? = null
    
    // 블루투스 권한 확인
    private val hasBluetoothPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    
    companion object {
        private const val TAG = "MeshChatManager"
        
        // 메시지 타입 상수
        private const val CHAT = "CHAT"
        private const val PRESENCE = "PRESENCE"
        private const val NICKNAME = "NICKNAME"
        
        // 메시지 캐시 크기
        private const val MESSAGE_CACHE_SIZE = 100
    }
    
    /**
     * 네트워크 초기화 및 시작
     */
    fun startNetwork() {
        if (!hasBluetoothPermission) {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot start network.")
            return
        }
        
        if (_isNetworkActive.value == true) {
            Log.d(TAG, "Network already active")
            return
        }
        
        Log.d(TAG, "Starting mesh network with nickname: $deviceNickname")
        
        // 기본 네트워크 키 생성
        val masterKey = "LanternMeshNetwork".toByteArray(StandardCharsets.UTF_8)
        securityManager.deriveKeys(masterKey)
        
        // 관리자 초기화
        initializeManagers()
        
        // 스캐닝 및 광고 시작
        startScanning()
        startAdvertising()
        
        // 상태 업데이트
        _isNetworkActive.postValue(true)
        
        // Wakelock 획득 (백그라운드 작업 유지)
        acquireWakeLock()
        
        // 주기적인 작업 스케줄링
        schedulePeriodicTasks()
    }
    
    /**
     * 네트워크 중지
     */
    fun stopNetwork() {
        if (_isNetworkActive.value != true) {
            Log.d(TAG, "Network already inactive")
            return
        }
        
        Log.d(TAG, "Stopping mesh network")
        
        // 스캐닝 및 광고 중지
        stopScanning()
        stopAdvertising()
        
        // Wakelock 해제
        releaseWakeLock()
        
        // 상태 업데이트
        _isNetworkActive.postValue(false)
    }
    
    /**
     * 메시지 수신 처리
     */
    private fun handleMeshMessage(message: MeshMessage) {
        // 메시지 처리 로직
        Log.d(TAG, "Received mesh message: ${message.sender}, seq=${message.sequenceNumber}")
        
        // 메시지 타입에 따라 처리
        when (message.messageType) {
            MessageType.CHAT -> handleChatMessage(message)
            MessageType.PRESENCE -> handlePresenceMessage(message)
            MessageType.NICKNAME_BROADCAST -> handleNicknameMessage(message)
            else -> Log.d(TAG, "Unhandled message type: ${message.messageType}")
        }
    }
    
    /**
     * 주변 노드 정보 업데이트
     */
    private fun updateNearbyNodeInfo(scanResult: ScanResult) {
        val device = scanResult.device
        val deviceAddress = device.address
        val rssi = scanResult.rssi
        
        // ScanRecord에서 닉네임 정보 추출 (기본값은 기기 이름)
        val nickname = scanResult.scanRecord?.deviceName ?: "Unknown"
        
        // 노드 정보 업데이트
        val node = nodeCache[deviceAddress]?.copy(
            rssi = rssi,
            nickname = nickname,
            lastSeen = System.currentTimeMillis()
        ) ?: NearbyNode(
            device = device,
            nickname = nickname,
            rssi = rssi
        )
        
        nodeCache[deviceAddress] = node
        
        // 주변 노드 목록 업데이트
        updateNearbyNodesList()
    }
    
    /**
     * 주변 노드 목록 업데이트
     * 30초 이상 탐지되지 않은 노드는 목록에서 제거
     */
    private fun updateNearbyNodesList() {
        val currentTime = System.currentTimeMillis()
        val nodeList = nodeCache.values.filter { 
            currentTime - it.lastSeen < 30000 // 30초
        }.sortedByDescending { it.rssi }
        
        _nearbyNodes.postValue(nodeList)
    }
    
    /**
     * 닉네임 설정
     */
    fun setDeviceNickname(nickname: String) {
        Log.d(TAG, "Setting device nickname: $nickname")
        this.deviceNickname = nickname
        
        // 닉네임 업데이트 브로드캐스트
        broadcastNickname()
    }
    
    /**
     * 닉네임 브로드캐스트
     * 메시 네트워크의 다른 노드에게 자신의 닉네임 정보 전송
     */
    private fun broadcastNickname() {
        if (!hasBluetoothPermission) {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot broadcast nickname.")
            return
        }
        
        Log.d(TAG, "Broadcasting nickname: $deviceNickname")
        
        // 닉네임을 바이트 배열로 변환
        val nicknameBytes = deviceNickname.toByteArray(StandardCharsets.UTF_8)
        
        // 브로드캐스트 주소로 닉네임 정보 전송
        networkLayer.send(
            destinationAddress = MeshPdu.BROADCAST_ADDRESS,
            messageType = MessageType.NICKNAME_BROADCAST,
            payload = nicknameBytes
        )
    }
    
    /**
     * 자신의 존재 브로드캐스트
     * 네트워크에 자신의 존재 알림
     */
    private fun broadcastPresence() {
        if (!hasBluetoothPermission) {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot broadcast presence.")
            return
        }
        
        Log.d(TAG, "Broadcasting presence")
        
        // 닉네임 브로드캐스트
        broadcastNickname()
        
        // TODO: 상태 정보 추가 구현 (배터리 상태, 장치 유형 등)
    }
    
    /**
     * 오래된 노드 정리
     * 일정 시간 동안 탐지되지 않은 노드 제거
     */
    private fun cleanupStaleNodes() {
        val currentTime = System.currentTimeMillis()
        
        // 60초 동안 탐지되지 않은 노드는 제거
        val staleNodeAddresses = nodeCache.entries
            .filter { currentTime - it.value.lastSeen > 60000 }
            .map { it.key }
        
        // 오래된 노드 제거
        staleNodeAddresses.forEach { address ->
            nodeCache.remove(address)
            Log.d(TAG, "Removed stale node: $address")
        }
        
        // 노드 목록 업데이트
        if (staleNodeAddresses.isNotEmpty()) {
            updateNearbyNodesList()
        }
    }
    
    /**
     * 그룹 채팅 내역 가져오기
     */
    suspend fun getGroupMessages(limit: Int = 100): List<ChatMessage> {
        Log.d(TAG, "Loading group messages, limit: $limit")
        
        try {
            // Flow를 List로 변환
            val messagesFlow = messageRepository.getGroupMessages(limit)
            return messagesFlow.firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading group messages", e)
            return emptyList()
        }
    }
    
    /**
     * 1:1 채팅 내역 가져오기
     */
    suspend fun getDirectMessages(partnerId: Short, limit: Int = 100): List<ChatMessage> {
        Log.d(TAG, "Loading direct messages with $partnerId, limit: $limit")
        
        try {
            // Flow를 List로 변환
            val messagesFlow = messageRepository.getDirectMessages(partnerId, limit)
            return messagesFlow.firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading direct messages", e)
            return emptyList()
        }
    }
    
    /**
     * 채팅 내역 가져오기 
     */
    suspend fun getAllMessages(limit: Int = 100): List<ChatMessage> {
        Log.d(TAG, "Loading all messages, limit: $limit")
        
        try {
            // Flow를 List로 변환
            val messagesFlow = messageRepository.getChatMessages(limit)
            return messagesFlow.firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading all messages", e)
            return emptyList()
        }
    }
    
    /**
     * 모든 메시지 불러오기
     * 로컬 데이터베이스에서 저장된 모든 메시지 불러오기
     */
    suspend fun loadAllMessages(): List<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            val savedMessagesFlow = messageRepository.getChatMessages()
            val savedMessages = savedMessagesFlow.firstOrNull() ?: emptyList()
            
            // 메시지 스토어에 저장된 메시지 추가
            val messagesCopy = savedMessages.toList()
            withContext(Dispatchers.Main) {
                synchronized(messageStore) {
                    messageStore.clear()
                    messageStore.addAll(messagesCopy)
                    _messages.value = messageStore.toList()
                }
            }
            
            return@withContext savedMessages
        } catch (e: Exception) {
            Log.e(TAG, "메시지 불러오기 중 오류 발생", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * 메시지 수신 확인 전송
     * 메시지를 성공적으로 수신했음을 발신자에게 알림
     */
    private fun sendAcknowledgement(messageId: Long, sourceAddress: Short) {
        if (!hasBluetoothPermission) {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot send acknowledgement.")
            return
        }
        
        Log.d(TAG, "Sending acknowledgement for message $messageId to $sourceAddress")
        
        // 메시지 ID를 바이트 배열로 변환
        val buffer = ByteBuffer.allocate(8).putLong(messageId)
        
        // 발신자에게 ACK 메시지 전송
        networkLayer.send(
            destinationAddress = sourceAddress,
            messageType = MessageType.ACKNOWLEDGEMENT,
            payload = buffer.array()
        )
    }
    
    /**
     * 관리자 컴포넌트 초기화 및 설정
     */
    private fun initializeManagers() {
        // 광고 매니저 설정
        advertiserManager.setNickname(deviceNickname)
        
        // 스캐너 매니저 설정
        // scannerManager.initialize()
        
        // 스캔 결과 리스너 설정
        // scannerManager.setScanResultListener { result ->
        //     // 스캔 결과 처리
        //     handleScanResult(result)
        // }
    }
    
    /**
     * BLE 스캐닝 시작
     */
    private fun startScanning() {
        if (!hasBluetoothPermission) {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot start scanning.")
            return
        }
        
        Log.d(TAG, "Starting BLE scanning")
        scannerManager.startScan()
    }
    
    /**
     * BLE 스캐닝 중지
     */
    private fun stopScanning() {
        if (!hasBluetoothPermission) {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot stop scanning.")
            return
        }
        
        Log.d(TAG, "Stopping BLE scanning")
        scannerManager.stopScan()
    }
    
    /**
     * BLE 광고 시작
     */
    private fun startAdvertising() {
        if (!hasBluetoothPermission) {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot start advertising.")
            return
        }
        
        Log.d(TAG, "Starting BLE advertising")
        advertiserManager.startAdvertising()
    }
    
    /**
     * BLE 광고 중지
     */
    private fun stopAdvertising() {
        if (!hasBluetoothPermission) {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot stop advertising.")
            return
        }
        
        Log.d(TAG, "Stopping BLE advertising")
        advertiserManager.stopAdvertising()
    }
    
    /**
     * Wakelock 획득 (백그라운드 작업 유지)
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Lantern:MeshNetworkWakeLock"
            )
            wakeLock?.acquire(10 * 60 * 1000L) // 10분 동안 유지
            Log.d(TAG, "Acquired wake lock")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring wake lock", e)
        }
    }
    
    /**
     * Wakelock 해제
     */
    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Released wake lock")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }
    }
    
    /**
     * 주기적인 작업 스케줄링
     */
    private fun schedulePeriodicTasks() {
        scope.launch {
            while (_isNetworkActive.value == true) {
                try {
                    // 주변 노드 정보 브로드캐스트
                    broadcastPresence()
                    
                    // 오래된 노드 제거
                    cleanupStaleNodes()
                    
                    delay(AdvertiserManager.PRESENCE_BROADCAST_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic tasks", e)
                }
            }
        }
    }
    
    /**
     * 스캔 결과 처리
     */
    private fun handleScanResult(result: ScanResult) {
        // 주변 디바이스 정보 업데이트
        updateNearbyNodeInfo(result)
        
        // 메쉬 메시지 처리
        networkLayer.handleIncomingScanResult(result)
    }
    
    /**
     * 그룹 메시지 전송
     * 모든 노드에게 메시지 전송
     */
    fun sendGroupMessage(content: String) {
        if (!hasBluetoothPermission) {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot send group message.")
            return
        }
        
        // 내용이 비어있는지 확인
        if (content.isBlank()) {
            Log.w(TAG, "Cannot send empty message")
            return
        }
        
        // 메시지 크기 확인
        val contentSize = content.toByteArray(StandardCharsets.UTF_8).size
        if (contentSize > 1000) { // 1KB 이상의 메시지는 거부
            Log.e(TAG, "Message too large: $contentSize bytes")
            return
        }
        
        Log.d(TAG, "Sending group message: $content")
        
        try {
            val message = ChatMessage(
                sender = ownAddress,
                senderNickname = deviceNickname,
                content = content,
                isDelivered = false, // 초기 상태: 전송 중
                isIncoming = false   // 내가 보낸 메시지
            )
            
            // 자신의 메시지 스토어에 추가
            addMessageToStore(message)
            
            // 메시지 직렬화
            val serializedMessage = serializeChatMessage(message)
            
            // 브로드캐스트 주소로 메시지 전송
            networkLayer.send(
                destinationAddress = MeshPdu.BROADCAST_ADDRESS,
                messageType = MessageType.CHAT_BROADCAST,
                payload = serializedMessage,
                callback = object : SendCallback {
                    override fun onSendStarted(messageId: Long) {
                        Log.d(TAG, "그룹 메시지 전송 시작: $messageId")
                    }
                    
                    override fun onSendCompleted(messageId: Long, success: Boolean) {
                        // 메시지 전송 완료 시 상태 업데이트
                        if (success) {
                            updateMessageDeliveryStatus(message.id, true)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "그룹 메시지 전송 중 오류 발생", e)
        }
    }
    
    /**
     * 1:1 메시지 전송
     * 특정 노드에게 메시지 전송
     */
    fun sendDirectMessage(recipient: Short, content: String) {
        if (!hasBluetoothPermission) {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT. Cannot send direct message.")
            return
        }
        
        // 내용이 비어있는지 확인
        if (content.isBlank()) {
            Log.w(TAG, "Cannot send empty message")
            return
        }
        
        // 메시지 크기 확인
        val contentSize = content.toByteArray(StandardCharsets.UTF_8).size
        if (contentSize > 1000) { // 1KB 이상의 메시지는 거부
            Log.e(TAG, "Message too large: $contentSize bytes")
            return
        }
        
        Log.d(TAG, "Sending direct message to $recipient: $content")
        
        try {
            val message = ChatMessage(
                sender = ownAddress,
                senderNickname = deviceNickname,
                recipient = recipient,
                content = content,
                isDelivered = false, // 초기 상태: 전송 중
                isIncoming = false   // 내가 보낸 메시지
            )
            
            // 자신의 메시지 스토어에 추가
            addMessageToStore(message)
            
            // 메시지 직렬화
            val serializedMessage = serializeChatMessage(message)
            
            // 특정 주소로 메시지 전송
            networkLayer.send(
                destinationAddress = recipient,
                messageType = MessageType.CHAT_UNICAST,
                payload = serializedMessage,
                callback = object : SendCallback {
                    override fun onSendStarted(messageId: Long) {
                        Log.d(TAG, "1:1 메시지 전송 시작: $messageId")
                    }
                    
                    override fun onSendCompleted(messageId: Long, success: Boolean) {
                        // 메시지 전송 완료 시 상태 업데이트
                        if (success) {
                            updateMessageDeliveryStatus(message.id, true)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "1:1 메시지 전송 중 오류 발생", e)
        }
    }
    
    /**
     * 주변 노드 탐색
     * 프로비저닝 가능한 노드 탐색
     */
    suspend fun scanForNodes(): List<NearbyNode> = withContext(Dispatchers.IO) {
        val devices = provisioningManager.discoverNodes()
        
        // 기기 목록을 NearbyNode 형식으로 변환
        return@withContext devices.map { device ->
            val nickname = device.name ?: "Unknown"
            val existingNode = nodeCache[device.address]
            
            existingNode?.copy(
                lastSeen = System.currentTimeMillis()
            ) ?: NearbyNode(
                device = device,
                nickname = nickname,
                rssi = -70 // 기본값
            )
        }
    }
    
    /**
     * 노드 프로비저닝
     * 메시 네트워크에 노드 추가
     */
    suspend fun provisionNode(device: BluetoothDevice): ProvisionResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting provisioning for ${device.address}")
        return@withContext provisioningManager.provision(device)
    }
    
    /**
     * 수신된 메시지 처리
     */
    private fun handleReceivedMessage(
        sourceAddress: Short,
        messageType: MessageType,
        payload: ByteArray,
        isUrgent: Boolean
    ) {
        when (messageType) {
            MessageType.CHAT_UNICAST, MessageType.CHAT_BROADCAST, 
            MessageType.URGENT_UNICAST, MessageType.URGENT_BROADCAST -> {
                // 채팅 메시지 처리
                val message = deserializeChatMessage(payload)
                if (message != null) {
                    // 메시지 저장 및 UI 업데이트
                    addMessageToStore(message)
                    
                    // ACK 메시지 전송 (유니캐스트 메시지인 경우)
                    if (messageType == MessageType.CHAT_UNICAST || messageType == MessageType.URGENT_UNICAST) {
                        // MeshPdu에서 추출한 messageId로 ACK 전송
                        // 직접적인 메시지 ID가 없으므로 메시지 UUID의 mostSignificantBits 사용
                        sendAcknowledgement(message.id.mostSignificantBits, sourceAddress)
                    }
                }
            }
            MessageType.NICKNAME_BROADCAST -> {
                // 닉네임 정보 처리
                handleNicknameUpdate(sourceAddress, payload)
            }
            MessageType.ACKNOWLEDGEMENT -> {
                // 확인 메시지 처리
                handleAcknowledgement(sourceAddress, payload)
            }
            else -> {
                Log.d(TAG, "Received message of type: $messageType from: $sourceAddress")
            }
        }
    }
    
    /**
     * 확인(ACK) 메시지 처리
     */
    private fun handleAcknowledgement(sourceAddress: Short, payload: ByteArray) {
        try {
            // ACK 메시지에서 원본 메시지 ID 추출
            val buffer = ByteBuffer.wrap(payload)
            val messageId = buffer.long
            
            Log.d(TAG, "Received ACK from $sourceAddress for message: $messageId")
            
            // 메시지 전송 완료 처리
            networkLayer.completeMessageSend(messageId, true)
            
            // 메시지 스토어에서 해당 메시지 ID와 관련된 UUID 찾기
            // 메시지 ID(Long)가 원본 메시지의 UUID.mostSignificantBits에 해당
            val messageUuid = findMessageUuidByLongId(messageId)
            if (messageUuid != null) {
                // 메시지 전송 상태 업데이트
                updateMessageDeliveryStatus(messageUuid, true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing acknowledgement", e)
        }
    }
    
    /**
     * Long 타입 ID로 메시지 UUID 찾기
     * MeshPdu의 messageId(Long)를 기반으로 ChatMessage의 UUID 찾기
     */
    private fun findMessageUuidByLongId(longId: Long): UUID? {
        synchronized(messageStore) {
            // mostSignificantBits가 longId와 일치하는 메시지 검색
            val message = messageStore.find { 
                it.id.mostSignificantBits == longId && 
                !it.isIncoming && // 내가 보낸 메시지만 검색
                !it.isDelivered   // 아직 전송 완료로 표시되지 않은 메시지
            }
            return message?.id
        }
    }
    
    /**
     * 메시지 저장소에 메시지 추가
     */
    private fun addMessageToStore(message: ChatMessage) {
        synchronized(messageStore) {
            // 중복 메시지 확인
            if (messageStore.none { it.id == message.id }) {
                messageStore.add(message)
                _messages.postValue(messageStore.toList())
                
                // 로컬 데이터베이스에 저장
                persistMessage(message)
            }
        }
    }
    
    /**
     * 메시지 직렬화
     * ChatMessage 객체를 바이트 배열로 변환
     */
    private fun serializeChatMessage(message: ChatMessage): ByteArray {
        // 메시지 크기 제한 설정
        val MAX_CONTENT_SIZE = 200 // 최대 메시지 길이 (바이트)
        val MAX_NICKNAME_SIZE = 30 // 최대 닉네임 길이 (바이트)
        
        // 내용 및 닉네임을 바이트 배열로 변환하고 필요시 잘라냄
        var contentBytes = message.content.toByteArray(StandardCharsets.UTF_8)
        var nicknameBytes = message.senderNickname.toByteArray(StandardCharsets.UTF_8)
        
        // 메시지 크기 제한
        if (contentBytes.size > MAX_CONTENT_SIZE) {
            val tempContent = ByteArray(MAX_CONTENT_SIZE)
            System.arraycopy(contentBytes, 0, tempContent, 0, MAX_CONTENT_SIZE)
            contentBytes = tempContent
            Log.w(TAG, "메시지 내용이 너무 길어 잘림: ${message.id}")
        }
        
        // 닉네임 크기 제한
        if (nicknameBytes.size > MAX_NICKNAME_SIZE) {
            val tempNickname = ByteArray(MAX_NICKNAME_SIZE)
            System.arraycopy(nicknameBytes, 0, tempNickname, 0, MAX_NICKNAME_SIZE)
            nicknameBytes = tempNickname
            Log.w(TAG, "닉네임이 너무 길어 잘림: ${message.id}")
        }
        
        try {
            val buffer = ByteBuffer.allocate(28 + nicknameBytes.size + contentBytes.size)
                .putLong(message.id.mostSignificantBits)    // UUID 상위 8바이트
                .putLong(message.id.leastSignificantBits)   // UUID 하위 8바이트
                .putShort(message.sender)                   // 발신자 주소 (2바이트)
                .putShort(message.recipient ?: 0)           // 수신자 주소 (2바이트)
                .putLong(message.timestamp)                 // 타임스탬프 (8바이트)
                .put(if (message.isUrgent) 1 else 0)        // 긴급 여부 (1바이트)
                .put(nicknameBytes.size.toByte())          // 닉네임 길이 (1바이트)
                .put(nicknameBytes)                        // 닉네임
                .put(contentBytes)                         // 메시지 내용
            
            return buffer.array()
        } catch (e: Exception) {
            Log.e(TAG, "메시지 직렬화 중 오류 발생", e)
            // 오류 발생 시 빈 메시지 반환
            return ByteBuffer.allocate(28 + nicknameBytes.size)
                .putLong(message.id.mostSignificantBits)
                .putLong(message.id.leastSignificantBits)
                .putShort(message.sender)
                .putShort(message.recipient ?: 0)
                .putLong(message.timestamp)
                .put(if (message.isUrgent) 1 else 0)
                .put(nicknameBytes.size.toByte())
                .put(nicknameBytes)
                .put("메시지 전송 오류 발생".toByteArray(StandardCharsets.UTF_8))
                .array()
        }
    }
    
    /**
     * 메시지 역직렬화
     * 바이트 배열을 ChatMessage 객체로 변환
     */
    private fun deserializeChatMessage(
        bytes: ByteArray
    ): ChatMessage? {
        try {
            val buffer = ByteBuffer.wrap(bytes)
            
            val idMost = buffer.long             // UUID 상위 8바이트
            val idLeast = buffer.long            // UUID 하위 8바이트
            val sender = buffer.short            // 발신자 주소
            val recipient = buffer.short         // 수신자 주소
            val timestamp = buffer.long          // 타임스탬프
            val urgent = buffer.get() == 1.toByte() // 긴급 여부
            
            val nicknameLength = buffer.get().toInt() // 닉네임 길이
            val nicknameBytes = ByteArray(nicknameLength)
            buffer.get(nicknameBytes)
            
            val contentLength = bytes.size - buffer.position()
            val contentBytes = ByteArray(contentLength)
            buffer.get(contentBytes)
            
            return ChatMessage(
                id = UUID(idMost, idLeast),
                sender = sender,
                senderNickname = String(nicknameBytes, StandardCharsets.UTF_8),
                recipient = if (recipient.toInt() == 0) null else recipient,
                content = String(contentBytes, StandardCharsets.UTF_8),
                timestamp = timestamp,
                isUrgent = urgent
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing chat message", e)
            return null
        }
    }
    
    /**
     * 메시지 영구 저장
     * 로컬 데이터베이스에 메시지 저장
     */
    private fun persistMessage(message: ChatMessage) {
        scope.launch(Dispatchers.IO) {
            try {
                val success = messageRepository.saveChatMessage(message)
                if (success) {
                    Log.d(TAG, "메시지가 성공적으로 저장되었습니다: ${message.id}")
                } else {
                    Log.w(TAG, "메시지 저장에 실패했습니다: ${message.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "메시지 저장 중 오류 발생", e)
            }
        }
    }
    
    /**
     * 리소스 해제
     */
    fun cleanup() {
        stopNetwork()
        transportLayer.shutdown()
        networkLayer.shutdown()
    }
    
    /**
     * 메시지 전송 상태 업데이트
     */
    private fun updateMessageDeliveryStatus(messageId: UUID, isDelivered: Boolean) {
        synchronized(messageStore) {
            // 메시지 스토어에서 해당 ID의 메시지 찾기
            val messageIndex = messageStore.indexOfFirst { it.id == messageId }
            if (messageIndex != -1) {
                // 메시지 상태 업데이트
                val message = messageStore[messageIndex].copy(isDelivered = isDelivered)
                messageStore[messageIndex] = message
                
                // 메시지 목록 상태 업데이트
                _messages.postValue(messageStore.toList())
                
                // 데이터베이스에 저장된 메시지 상태 업데이트
                updateMessageStatusInDatabase(message)
            }
        }
    }
    
    /**
     * 데이터베이스의 메시지 상태 업데이트
     */
    private fun updateMessageStatusInDatabase(message: ChatMessage) {
        scope.launch(Dispatchers.IO) {
            try {
                val success = messageRepository.updateMessageStatus(message.id.toString(), message.isDelivered)
                if (!success) {
                    Log.w(TAG, "데이터베이스 메시지 상태 업데이트 실패: ${message.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "메시지 상태 업데이트 중 오류 발생", e)
            }
        }
    }
    
    /**
     * 닉네임 업데이트 처리
     */
    private fun handleNicknameUpdate(sourceAddress: Short, payload: ByteArray) {
        try {
            val nickname = String(payload, StandardCharsets.UTF_8)
            Log.d(TAG, "Received nickname update from $sourceAddress: $nickname")
            
            // 주변 노드 캐시에서 해당 주소를 가진 노드 찾기
            nodeCache.values.find { it.address == sourceAddress }?.let { node ->
                // 닉네임 업데이트
                node.device?.address?.let { deviceAddress ->
                    nodeCache[deviceAddress] = node.copy(nickname = nickname)
                    updateNearbyNodesList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing nickname update", e)
        }
    }
    
    /**
     * 채팅 메시지 처리
     */
    private fun handleChatMessage(message: MeshMessage) {
        Log.d(TAG, "Handling chat message: ${message.content}")
        
        // 1. 메시지 객체 생성
        val chatMessage = ChatMessage.fromMeshMessage(message, true)
        
        // 2. 메시지 저장
        scope.launch {
            messageRepository.saveChatMessage(chatMessage)
        }
        
        // 3. 메시지 목록에 추가
        synchronized(messageStore) {
            messageStore.add(chatMessage)
            
            // UI 갱신
            scope.launch(Dispatchers.Main) {
                _messages.value = messageStore.toList()
            }
        }
    }
    
    /**
     * 존재 알림 메시지 처리
     */
    private fun handlePresenceMessage(message: MeshMessage) {
        Log.d(TAG, "Handling presence message from: ${message.sender}")
        
        // 1. 노드 캐시 업데이트
        val deviceAddress = message.sender
        val nickname = message.senderNickname
        
        // 2. 노드 목록 업데이트 (존재 알림이 있으면 온라인으로 간주)
        val device = getBluetoothDeviceByAddress(deviceAddress)
        if (device != null) {
            val node = NearbyNode(
                device = device,
                nickname = nickname,
                rssi = 0, // 신호 강도는 존재 메시지에서 알 수 없음
                lastSeen = System.currentTimeMillis()
            )
            
            nodeCache[deviceAddress] = node
            
            // 노드 목록 업데이트
            updateNearbyNodesList()
        }
    }
    
    /**
     * 닉네임 업데이트 메시지 처리
     */
    private fun handleNicknameMessage(message: MeshMessage) {
        Log.d(TAG, "Handling nickname message: ${message.senderNickname}")
        
        // 1. 노드 캐시에서 기존 노드 정보 찾기
        val deviceAddress = message.sender
        val node = nodeCache[deviceAddress]
        
        // 2. 닉네임 업데이트
        if (node != null) {
            nodeCache[deviceAddress] = node.copy(
                nickname = message.senderNickname,
                lastSeen = System.currentTimeMillis()
            )
            
            // 노드 목록 업데이트
            updateNearbyNodesList()
        }
    }
    
    /**
     * 블루투스 주소로 BluetoothDevice 객체 찾기
     */
    private fun getBluetoothDeviceByAddress(address: String): BluetoothDevice? {
        // 주변 노드 캐시에서 해당 주소와 일치하는 노드 찾기
        return nodeCache.values.find { it.device?.address == address }?.device
    }
} 