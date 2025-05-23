package com.ssafy.lanterns.data.source.ble.mesh

import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.util.Log
import android.util.LruCache
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 메시지 타입 열거형
 * 메시지의 종류를 나타냄
 */
enum class MessageType(val value: Byte) {
    // 기본 메시지 타입 (MeshMessage.kt와 호환성 유지)
    CHAT(0x01),                 // 채팅 메시지 (기본)
    PRESENCE(0x02),             // 상태 메시지 (디바이스 존재 알림)
    ACK(0x03),                  // 확인 메시지
    REQUEST(0x04),              // 요청 메시지
    
    // 세분화된 메시지 타입
    CHAT_UNICAST(0x11),         // 1:1 채팅 메시지
    CHAT_BROADCAST(0x12),       // 그룹 채팅 메시지
    NICKNAME_BROADCAST(0x13),   // 닉네임 정보 브로드캐스트
    PROVISIONING_REQUEST(0x14), // 프로비저닝 요청
    PROVISIONING_RESPONSE(0x15), // 프로비저닝 응답
    ACKNOWLEDGEMENT(0x16),      // 메시지 수신 확인 (ACK)
    
    // 긴급 메시지 타입
    URGENT_UNICAST(0x81.toByte()),      // 긴급 1:1 메시지 (QoS 레이어)
    URGENT_BROADCAST(0x82.toByte());    // 긴급 그룹 메시지 (QoS 레이어)
    
    companion object {
        private val map = MessageType.values().associateBy(MessageType::value)
        fun fromByte(type: Byte): MessageType = map[type] ?: CHAT_BROADCAST
    }
}

/**
 * Mesh PDU 데이터 클래스
 * Mesh 네트워크의 기본 전송 단위
 */
data class MeshPdu(
    val messageId: Long,         // 메시지 고유 식별자
    val srcAddress: Short,       // 발신 노드 주소
    val dstAddress: Short,       // 수신 노드 주소 (0xFFFF: 브로드캐스트)
    var ttl: Byte,               // Time-To-Live (홉 카운트)
    val type: MessageType,       // 메시지 타입
    val body: ByteArray          // 메시지 본문 (Transport Layer로부터 전달받은 세그먼트)
) {
    /**
     * ByteArray로 직렬화
     */
    fun toByteArray(): ByteArray {
        return ByteBuffer.allocate(HEADER_SIZE + body.size)
            .putLong(messageId)          // 메시지 ID (8바이트)
            .putShort(srcAddress)        // 발신 주소 (2바이트)
            .putShort(dstAddress)        // 수신 주소 (2바이트) 
            .put(ttl)                    // TTL (1바이트)
            .put(type.value)             // 메시지 타입 (1바이트)
            .put(body)                   // 메시지 본문
            .array()
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as MeshPdu
        
        if (messageId != other.messageId) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        return messageId.hashCode()
    }
    
    companion object {
        // PDU 헤더 크기: messageId(8) + srcAddress(2) + dstAddress(2) + ttl(1) + type(1) = 14 바이트
        const val HEADER_SIZE = 14
        
        // 브로드캐스트 주소
        const val BROADCAST_ADDRESS: Short = 0xFFFF.toShort()
        
        /**
         * ByteArray에서 MeshPdu 파싱
         */
        fun fromByteArray(bytes: ByteArray): MeshPdu? {
            if (bytes.size < HEADER_SIZE) {
                return null
            }
            
            val buffer = ByteBuffer.wrap(bytes)
            val messageId = buffer.long       // 메시지 ID
            val srcAddress = buffer.short     // 발신 주소
            val dstAddress = buffer.short     // 수신 주소
            val ttl = buffer.get()            // TTL
            val type = MessageType.fromByte(buffer.get()) // 메시지 타입
            
            // 메시지 본문 추출
            val bodySize = bytes.size - HEADER_SIZE
            val body = ByteArray(bodySize)
            System.arraycopy(bytes, HEADER_SIZE, body, 0, bodySize)
            
            return MeshPdu(messageId, srcAddress, dstAddress, ttl, type, body)
        }
        
        /**
         * 광고 데이터(ScanRecord)에서 MeshPdu 추출
         */
        fun fromScanRecord(scanRecord: ScanRecord?, manufacturerId: Int): MeshPdu? {
            if (scanRecord == null) return null
            
            // 제조사별 데이터 추출
            val manufacturerData = scanRecord.getManufacturerSpecificData(manufacturerId)
            if (manufacturerData == null || manufacturerData.size < HEADER_SIZE) {
                return null
            }
            
            return fromByteArray(manufacturerData)
        }
    }
}

/**
 * 메시지 전송 상태 콜백 인터페이스
 * 메시지 전송 과정의 상태 변화를 통지받기 위한 인터페이스
 */
interface SendCallback {
    /**
     * 메시지 전송 시작 콜백
     * @param messageId 메시지 ID
     */
    fun onSendStarted(messageId: Long)
    
    /**
     * 메시지 전송 완료 콜백
     * @param messageId 메시지 ID
     * @param success 전송 성공 여부
     */
    fun onSendCompleted(messageId: Long, success: Boolean)
}

/**
 * Mesh 네트워크 계층
 * 메시지 중복 제거, TTL 관리, 메시지 전파를 담당
 */
class MeshNetworkLayer(
    private val bleComm: BleComm,
    private val transportLayer: TransportLayer,
    private val ownAddress: Short
) {
    private val TAG = "MeshNetworkLayer"
    
    // 메시지 캐시: messageId -> timestamp
    private val messageCache = LruCache<Long, Long>(MESSAGE_CACHE_SIZE)
    
    // 진행 중인 메시지: ID와 재전송 카운트를 추적
    private val pendingMessages = ConcurrentHashMap<Long, MessageInfo>()
    
    // 메시지 전송 콜백: messageId -> callback
    private val sendCallbacks = ConcurrentHashMap<Long, SendCallback>()
    
    // 메시지 처리용 스케줄러
    private val scheduler = Executors.newScheduledThreadPool(1)
    
    // 애플리케이션 계층 콜백
    private var applicationCallback: OnMeshMessageCallback? = null
    
    // 메시지 전송 성공 여부 체크를 위한 타임아웃 (밀리초)
    private val MESSAGE_SEND_TIMEOUT_MS = 10000L
    
    init {
        // 전송 계층에 재전송 리스너 등록
        transportLayer.setOnRetransmissionListener(object : TransportLayer.OnRetransmissionListener {
            override fun onSegmentRetransmissionNeeded(segment: ByteArray) {
                // 세그먼트 재전송
                retransmitSegment(segment)
            }
        })
        
        // 주기적으로 메시지 캐시 정리
        scheduler.scheduleAtFixedRate({
            cleanupMessageCache()
        }, 30, 30, TimeUnit.SECONDS)
        
        // 주기적으로 메시지 전송 상태 확인
        scheduler.scheduleAtFixedRate({
            checkPendingMessages()
        }, 1, 1, TimeUnit.SECONDS)
    }
    
    /**
     * BLE 스캔 결과 처리
     * ScanResult에서 Mesh PDU를 추출하고 처리
     */
    fun handleIncomingScanResult(scanResult: ScanResult) {
        val scanRecord = scanResult.scanRecord ?: return
        
        // 스캔 레코드에서 Mesh PDU 추출
        val pdu = MeshPdu.fromScanRecord(scanRecord, MANUFACTURER_ID)
        if (pdu != null) {
            handleIncomingPdu(pdu)
        }
    }
    
    /**
     * 수신된 Mesh PDU 처리
     */
    private fun handleIncomingPdu(pdu: MeshPdu) {
        // 중복 메시지 검사
        if (isDuplicateMessage(pdu.messageId)) {
            Log.d(TAG, "Ignoring duplicate message: ${pdu.messageId}")
            return
        }
        
        // 메시지 캐시에 추가
        cacheMessage(pdu.messageId)
        
        // TTL 검사
        if (pdu.ttl <= 0) {
            Log.d(TAG, "Ignoring message with expired TTL: ${pdu.messageId}")
            return
        }
        
        // 목적지 주소 검사
        val isBroadcast = pdu.dstAddress == MeshPdu.BROADCAST_ADDRESS
        val isForMe = pdu.dstAddress == ownAddress
        
        // 타겟이 나인 경우 또는 브로드캐스트인 경우 처리
        if (isForMe || isBroadcast) {
            // Transport Layer에 세그먼트 전달
            val payload = transportLayer.addSegment(pdu.body)
            if (payload != null) {
                Log.d(TAG, "Message reassembled, sending to application layer")
                // 완성된 메시지는 애플리케이션 계층으로 전달
                deliverToApplication(pdu.srcAddress, pdu.type, payload)
                
                // ACK 타입이 아닌 경우 ACK 메시지 전송 (추후 개발)
                if (pdu.type != MessageType.ACK && pdu.type != MessageType.ACKNOWLEDGEMENT) {
                    // TODO: ACK 메시지 전송 로직 구현
                }
            }
        }
        
        // TTL > 0인 경우 메시지 재전파
        if (pdu.ttl > 0) {
            // TTL 감소
            pdu.ttl--
            
            // 메시지 재전파
            relayMessage(pdu)
        }
    }
    
    /**
     * 메시지 전송 (브로드캐스트/유니캐스트)
     * @param destinationAddress 목적지 주소 (브로드캐스트: MeshPdu.BROADCAST_ADDRESS)
     * @param messageType 메시지 타입
     * @param payload 메시지 페이로드
     * @param callback 전송 상태 콜백 (선택사항)
     * @return 생성된 메시지 ID
     */
    fun send(
        destinationAddress: Short, 
        messageType: MessageType, 
        payload: ByteArray,
        callback: SendCallback? = null
    ): Long {
        val messageId = generateMessageId()
        val seqNum = generateSequenceNumber().toShort()
        
        Log.d(TAG, "Sending message: id=$messageId, type=${messageType.name}, " +
              "to=${if (destinationAddress == MeshPdu.BROADCAST_ADDRESS) "BROADCAST" else destinationAddress}")
        
        // 메시지 진행 정보 저장
        pendingMessages[messageId] = MessageInfo(messageId, System.currentTimeMillis())
        
        // 콜백이 있는 경우 등록
        if (callback != null) {
            sendCallbacks[messageId] = callback
            callback.onSendStarted(messageId)
        }
        
        // Transport Layer에서 메시지 분할
        val segments = transportLayer.segment(payload, seqNum)
        
        // 각 세그먼트마다 Mesh PDU 생성 및 전송
        for (segment in segments) {
            val pdu = MeshPdu(
                messageId = messageId,
                srcAddress = ownAddress,
                dstAddress = destinationAddress,
                ttl = MAX_TTL,
                type = messageType,
                body = segment
            )
            
            // 메시지 캐시에 추가 (자신의 메시지도 캐싱)
            cacheMessage(messageId)
            
            // BLE 통신 계층을 통해 광고
            bleComm.startAdvertising(pdu.toByteArray())
        }
        
        return messageId
    }
    
    /**
     * 진행 중인 메시지 상태 체크
     */
    private fun checkPendingMessages() {
        val currentTime = System.currentTimeMillis()
        val messagesToComplete = mutableListOf<Long>()
        
        // 타임아웃된 메시지 검색
        pendingMessages.forEach { (messageId, info) ->
            if (currentTime - info.timestamp > MESSAGE_SEND_TIMEOUT_MS) {
                // 타임아웃 처리
                messagesToComplete.add(messageId)
            }
        }
        
        // 타임아웃된 메시지 처리
        messagesToComplete.forEach { messageId ->
            val messageInfo = pendingMessages.remove(messageId)
            val callback = sendCallbacks.remove(messageId)
            
            // 콜백 호출 (실패로 처리)
            callback?.onSendCompleted(messageId, false)
            
            if (messageInfo != null) {
                Log.d(TAG, "Message send timeout: $messageId")
            }
        }
    }
    
    /**
     * 수신된 메시지를 애플리케이션 계층으로 전달
     */
    private fun deliverToApplication(sourceAddress: Short, messageType: MessageType, payload: ByteArray) {
        // 우선순위에 따른 처리 (QoS 레이어)
        val isUrgent = when (messageType) {
            MessageType.URGENT_UNICAST, MessageType.URGENT_BROADCAST -> true
            else -> false
        }
        
        // 애플리케이션 콜백 호출
        applicationCallback?.onMessageReceived(sourceAddress, messageType, payload, isUrgent)
    }
    
    /**
     * 메시지 재전파 (Flooding)
     */
    private fun relayMessage(pdu: MeshPdu) {
        // 자신이 보낸 메시지는 재전파하지 않음
        if (pdu.srcAddress == ownAddress) {
            return
        }
        
        Log.d(TAG, "Relaying message: id=${pdu.messageId}, TTL=${pdu.ttl}")
        
        // BLE 통신 계층을 통해 광고
        bleComm.startAdvertising(pdu.toByteArray())
    }
    
    /**
     * 세그먼트 재전송
     */
    private fun retransmitSegment(segment: ByteArray) {
        try {
            // 세그먼트로부터 PDU 파싱
            val pdu = MeshPdu.fromByteArray(segment)
            if (pdu != null) {
                Log.d(TAG, "Retransmitting segment for message: ${pdu.messageId}")
                bleComm.startAdvertising(segment)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retransmitting segment", e)
        }
    }
    
    /**
     * 메시지 중복 여부 확인
     */
    private fun isDuplicateMessage(messageId: Long): Boolean {
        return messageCache.get(messageId) != null
    }
    
    /**
     * 메시지 캐시에 추가
     */
    private fun cacheMessage(messageId: Long) {
        messageCache.put(messageId, System.currentTimeMillis())
    }
    
    /**
     * 오래된 캐시 항목 정리
     */
    private fun cleanupMessageCache() {
        val currentTime = System.currentTimeMillis()
        
        // 만료된 메시지 검색
        val keysToRemove = mutableListOf<Long>()
        for (i in 0 until messageCache.size()) {
            val key = messageCache.snapshot().keys.elementAt(i)
            val timestamp = messageCache.get(key) ?: continue
            
            if (currentTime - timestamp > MESSAGE_CACHE_TTL_MS) {
                keysToRemove.add(key)
            }
        }
        
        // 만료된 메시지 제거
        for (key in keysToRemove) {
            messageCache.remove(key)
        }
        
        if (keysToRemove.isNotEmpty()) {
            Log.d(TAG, "Cleaned up ${keysToRemove.size} expired messages from cache")
        }
    }
    
    /**
     * 메시지 ID 생성
     */
    private fun generateMessageId(): Long {
        return UUID.randomUUID().mostSignificantBits
    }
    
    /**
     * 시퀀스 번호 생성
     */
    private fun generateSequenceNumber(): Int {
        return (System.nanoTime() % Int.MAX_VALUE).toInt()
    }
    
    /**
     * 특정 메시지 ID의 전송 완료 처리
     * 메시지가 성공적으로 전달됐다고 가정함
     */
    fun completeMessageSend(messageId: Long, success: Boolean = true) {
        val messageInfo = pendingMessages.remove(messageId)
        val callback = sendCallbacks.remove(messageId)
        
        if (messageInfo != null) {
            Log.d(TAG, "Message send completed: $messageId, success=$success")
            callback?.onSendCompleted(messageId, success)
        }
    }
    
    /**
     * 애플리케이션 콜백 설정
     */
    fun setOnMeshMessageCallback(callback: OnMeshMessageCallback) {
        this.applicationCallback = callback
    }
    
    /**
     * 메시지 진행 상태 정보
     */
    private data class MessageInfo(
        val messageId: Long,
        val timestamp: Long,
        var retryCount: Int = 0
    )
    
    /**
     * 리소스 해제
     */
    fun shutdown() {
        scheduler.shutdownNow()
    }
    
    /**
     * 애플리케이션 콜백 인터페이스
     */
    interface OnMeshMessageCallback {
        /**
         * 메시지 수신 콜백
         * @param sourceAddress 발신 노드 주소
         * @param messageType 메시지 타입
         * @param payload 메시지 페이로드 (재조립 완료된 데이터)
         * @param isUrgent 긴급 메시지 여부
         */
        fun onMessageReceived(sourceAddress: Short, messageType: MessageType, payload: ByteArray, isUrgent: Boolean)
    }
    
    companion object {
        // 제조사 ID
        const val MANUFACTURER_ID = 0xFFFF
        
        // 메시지 캐시 크기
        private const val MESSAGE_CACHE_SIZE = 1000
        
        // 메시지 캐시 TTL (5분)
        private const val MESSAGE_CACHE_TTL_MS = 300000L
        
        // 최대 TTL 값
        const val MAX_TTL: Byte = 10
    }
} 