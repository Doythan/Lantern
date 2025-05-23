package com.ssafy.lanterns.data.source.ble.mesh

import android.util.Log
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * 전송 계층 (Transport Layer)
 * 대용량 메시지를 BLE PDU 크기에 맞게 분할하고 재조립하는 역할 담당
 */
class TransportLayer {
    
    private val TAG = "TransportLayer"
    
    // 최대 PDU 크기 (잘림 방지를 위해 여유 있게 설정)
    private val MAX_SEGMENT_PAYLOAD_SIZE = 16 // 20바이트 실제 가용 크기 중 헤더 공간 제외
    
    // 재조립 중인 메시지 캐시: messageId -> List<Fragment>
    private val reassemblyCache = ConcurrentHashMap<Short, ReassemblyInfo>()
    
    // 세그먼트 타임아웃 처리를 위한 스케줄러
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    
    // 재전송 큐
    private val retransmissionQueue = ConcurrentLinkedQueue<SegmentInfo>()
    
    init {
        // 주기적으로 타임아웃된 세그먼트 정리
        scheduler.scheduleAtFixedRate({
            cleanupTimedOutMessages()
        }, 1, 1, TimeUnit.SECONDS)
        
        // 주기적으로 재전송 큐 처리
        scheduler.scheduleAtFixedRate({
            processRetransmissionQueue()
        }, 0, 500, TimeUnit.MILLISECONDS) // 500ms마다 처리
    }
    
    /**
     * 메시지 분할 (Segmentation)
     * 바이트 배열 메시지를 여러 작은 세그먼트로 분할
     * 
     * @param payload 애플리케이션 계층에서 전달받은 원본 메시지
     * @param seqNum 메시지의 시퀀스 번호 (고유 식별자)
     * @return 분할된 세그먼트 목록
     */
    fun segment(payload: ByteArray, seqNum: Short): List<ByteArray> {
        if (payload.isEmpty()) {
            return emptyList()
        }
        
        val segments = mutableListOf<ByteArray>()
        
        // 페이로드 크기에 따라 분할 필요 여부 결정
        if (payload.size <= MAX_SEGMENT_PAYLOAD_SIZE) {
            // 단일 세그먼트로 처리 가능
            val segment = ByteBuffer.allocate(payload.size + HEADER_SIZE)
                .putShort(seqNum)                  // 시퀀스 번호 (2바이트)
                .put(0)                           // 세그먼트 인덱스 (1바이트)
                .put(1)                           // 총 세그먼트 수 (1바이트)
                .put(payload)                     // 페이로드
                .array()
            segments.add(segment)
            Log.d(TAG, "Message with seqNum=$seqNum will be sent as single segment")
        } else {
            // 여러 세그먼트로 분할 필요
            val totalSegments = (payload.size + MAX_SEGMENT_PAYLOAD_SIZE - 1) / MAX_SEGMENT_PAYLOAD_SIZE
            if (totalSegments > 255) {
                // 세그먼트 수가 너무 많은 경우 (255개 초과)
                Log.e(TAG, "Message too large: requires $totalSegments segments (max 255)")
                return emptyList()
            }
            
            Log.d(TAG, "Message with seqNum=$seqNum will be split into $totalSegments segments")
            
            // 세그먼트 분할
            for (i in 0 until totalSegments) {
                val startPos = i * MAX_SEGMENT_PAYLOAD_SIZE
                val endPos = minOf(startPos + MAX_SEGMENT_PAYLOAD_SIZE, payload.size)
                val size = endPos - startPos
                
                val segmentPayload = ByteArray(size)
                System.arraycopy(payload, startPos, segmentPayload, 0, size)
                
                val segment = ByteBuffer.allocate(size + HEADER_SIZE)
                    .putShort(seqNum)                  // 시퀀스 번호 (2바이트)
                    .put(i.toByte())                   // 세그먼트 인덱스 (1바이트)
                    .put(totalSegments.toByte())       // 총 세그먼트 수 (1바이트)
                    .put(segmentPayload)               // 페이로드
                    .array()
                
                segments.add(segment)
                
                // 재전송 큐에 추가
                retransmissionQueue.add(
                    SegmentInfo(
                        seqNum = seqNum,
                        segmentIndex = i.toByte(),
                        totalSegments = totalSegments.toByte(),
                        data = segment,
                        timestamp = System.currentTimeMillis(),
                        retryCount = 0
                    )
                )
            }
        }
        
        return segments
    }
    
    /**
     * 세그먼트 추가 및 재조립 시도
     * Network Layer로부터 수신된 세그먼트를 추가하고, 가능하면 전체 메시지 재조립
     * 
     * @param segment 수신된 세그먼트 바이트 배열
     * @return 재조립 완료된 원본 메시지 또는 null (재조립이 아직 완료되지 않은 경우)
     */
    fun addSegment(segment: ByteArray): ByteArray? {
        if (segment.size < HEADER_SIZE) {
            Log.e(TAG, "Segment too small to contain header: ${segment.size} bytes")
            return null
        }
        
        // 헤더 파싱
        val buffer = ByteBuffer.wrap(segment)
        val seqNum = buffer.short       // 시퀀스 번호
        val segmentIndex = buffer.get() // 세그먼트 인덱스
        val totalSegments = buffer.get() // 총 세그먼트 수
        
        // 페이로드 추출
        val payloadSize = segment.size - HEADER_SIZE
        val payload = ByteArray(payloadSize)
        System.arraycopy(segment, HEADER_SIZE, payload, 0, payloadSize)
        
        Log.d(TAG, "Received segment: seqNum=$seqNum, index=$segmentIndex, total=$totalSegments")
        
        // 재조립 정보 가져오기 또는 새로 생성
        val reassemblyInfo = reassemblyCache.computeIfAbsent(seqNum) {
            ReassemblyInfo(
                seqNum = seqNum,
                totalSegments = totalSegments,
                segments = arrayOfNulls(totalSegments.toInt()),
                timestamp = System.currentTimeMillis()
            )
        }
        
        // 세그먼트 저장
        reassemblyInfo.segments[segmentIndex.toInt()] = payload
        reassemblyInfo.receivedCount++
        reassemblyInfo.timestamp = System.currentTimeMillis() // 타임아웃 갱신
        
        // 모든 세그먼트가 도착했는지 확인
        if (reassemblyInfo.receivedCount == totalSegments.toInt()) {
            // 전체 재조립
            return reassemble(seqNum)
        }
        
        return null
    }
    
    /**
     * 메시지 재조립 (Reassembly)
     * 캐시된 세그먼트를 사용하여 원본 메시지로 재조립
     * 
     * @param seqNum 메시지 시퀀스 번호
     * @return 재조립된 메시지 또는 null (재조립 실패)
     */
    fun reassemble(seqNum: Short): ByteArray? {
        val reassemblyInfo = reassemblyCache.remove(seqNum) ?: return null
        
        // 모든 세그먼트가 있는지 확인
        if (reassemblyInfo.receivedCount != reassemblyInfo.totalSegments.toInt()) {
            Log.w(TAG, "Cannot reassemble message $seqNum: missing segments, got ${reassemblyInfo.receivedCount}/${reassemblyInfo.totalSegments}")
            return null
        }
        
        // 전체 페이로드 크기 계산
        var totalSize = 0
        for (segment in reassemblyInfo.segments) {
            if (segment == null) {
                Log.e(TAG, "Unexpected null segment in reassembly for message $seqNum")
                return null
            }
            totalSize += segment.size
        }
        
        // 재조립
        val result = ByteArray(totalSize)
        var offset = 0
        
        for (segment in reassemblyInfo.segments) {
            if (segment == null) continue // 이미 위에서 확인했으므로 발생하지 않아야 함
            
            System.arraycopy(segment, 0, result, offset, segment.size)
            offset += segment.size
        }
        
        Log.d(TAG, "Successfully reassembled message $seqNum: $totalSize bytes")
        
        // 재전송 큐에서 관련 항목 제거
        removeFromRetransmissionQueue(seqNum)
        
        return result
    }
    
    /**
     * 타임아웃된 메시지 정리
     * 일정 시간 이상 재조립이 완료되지 않은 메시지를 캐시에서 제거
     */
    private fun cleanupTimedOutMessages() {
        val currentTime = System.currentTimeMillis()
        val timedOutSeqNums = mutableListOf<Short>()
        
        // 타임아웃된 메시지 찾기
        for ((seqNum, reassemblyInfo) in reassemblyCache) {
            if (currentTime - reassemblyInfo.timestamp > REASSEMBLY_TIMEOUT_MS) {
                timedOutSeqNums.add(seqNum)
                Log.w(TAG, "Message $seqNum timed out with ${reassemblyInfo.receivedCount}/${reassemblyInfo.totalSegments} segments")
            }
        }
        
        // 타임아웃된 메시지 제거
        for (seqNum in timedOutSeqNums) {
            reassemblyCache.remove(seqNum)
        }
    }
    
    /**
     * 재전송 큐 처리
     * 주기적으로 호출되어 재전송이 필요한 세그먼트를 처리
     */
    private fun processRetransmissionQueue() {
        if (retransmissionQueue.isEmpty()) return
        
        val currentTime = System.currentTimeMillis()
        val maxItemsToProcess = 5 // 한 번에 처리할 최대 아이템 수
        var processedCount = 0
        
        val iterator = retransmissionQueue.iterator()
        while (iterator.hasNext() && processedCount < maxItemsToProcess) {
            val segment = iterator.next()
            
            // 이미 너무 많이 재전송했거나 오래된 세그먼트면 제거
            if (segment.retryCount >= MAX_RETRANSMISSION_RETRIES || 
                currentTime - segment.timestamp > RETRANSMISSION_MAX_AGE_MS) {
                iterator.remove()
                continue
            }
            
            // 재전송 시간이 되었는지 확인
            if (currentTime - segment.timestamp > RETRANSMISSION_INTERVAL_MS) {
                // 여기서 실제 재전송 로직 구현
                // 현재는 Listener를 통해 알림만 제공
                onRetransmissionListener?.onSegmentRetransmissionNeeded(segment.data)
                
                // 재전송 상태 업데이트
                segment.retryCount++
                segment.timestamp = currentTime
                
                processedCount++
            }
        }
    }
    
    /**
     * 특정 시퀀스 번호의 세그먼트를 재전송 큐에서 제거
     */
    private fun removeFromRetransmissionQueue(seqNum: Short) {
        val iterator = retransmissionQueue.iterator()
        while (iterator.hasNext()) {
            val segment = iterator.next()
            if (segment.seqNum == seqNum) {
                iterator.remove()
            }
        }
    }
    
    /**
     * 리소스 해제
     */
    fun shutdown() {
        scheduler.shutdownNow()
    }
    
    /**
     * 세그먼트 재전송 리스너 인터페이스
     */
    interface OnRetransmissionListener {
        fun onSegmentRetransmissionNeeded(segment: ByteArray)
    }
    
    // 재전송 리스너
    private var onRetransmissionListener: OnRetransmissionListener? = null
    
    /**
     * 재전송 리스너 설정
     */
    fun setOnRetransmissionListener(listener: OnRetransmissionListener) {
        this.onRetransmissionListener = listener
    }
    
    /**
     * 재조립 정보 데이터 클래스
     */
    private data class ReassemblyInfo(
        val seqNum: Short,
        val totalSegments: Byte,
        val segments: Array<ByteArray?>,
        var receivedCount: Int = 0,
        var timestamp: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as ReassemblyInfo
            
            if (seqNum != other.seqNum) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            return seqNum.toInt()
        }
    }
    
    /**
     * 세그먼트 정보 데이터 클래스 (재전송용)
     */
    data class SegmentInfo(
        val seqNum: Short,
        val segmentIndex: Byte,
        val totalSegments: Byte,
        val data: ByteArray,
        var timestamp: Long,
        var retryCount: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as SegmentInfo
            
            if (seqNum != other.seqNum) return false
            if (segmentIndex != other.segmentIndex) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = seqNum.toInt()
            result = 31 * result + segmentIndex
            return result
        }
    }
    
    companion object {
        // 헤더 크기 (시퀀스 번호 + 세그먼트 인덱스 + 총 세그먼트 수)
        private const val HEADER_SIZE = 4 // 2 + 1 + 1 바이트
        
        // 재조립 타임아웃
        private const val REASSEMBLY_TIMEOUT_MS = 10000L // 10초
        
        // 재전송 관련 상수
        private const val RETRANSMISSION_INTERVAL_MS = 1000L // 1초
        private const val RETRANSMISSION_MAX_AGE_MS = 30000L // 30초
        private const val MAX_RETRANSMISSION_RETRIES = 5 // 최대 재전송 횟수
    }
} 