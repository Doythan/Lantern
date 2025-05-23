package com.ssafy.lanterns.data.source.ble.mesh

import android.content.Context
import android.provider.Settings
import android.util.Log
import java.util.Random
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * 메시지 유틸리티 클래스
 * 메시지 압축, 분할, 결합 등의 유틸리티 함수 제공
 */
object MessageUtils {
    private const val TAG = "MessageUtils"
    
    // 최대 광고 데이터 크기 (BLE 광고 제한)
    const val MAX_ADVERTISEMENT_SIZE = 24 // 31바이트에서 헤더 등을 제외한 실제 데이터 크기
    
    // 최대 GATT 패킷 크기
    const val MAX_GATT_PACKET_SIZE = 512
    
    // 메시지 캐시 크기 (각 발신자별 최대 저장 메시지 수)
    const val MAX_CACHE_SIZE_PER_SENDER = 100
    
    /**
     * 메시지를 광고 데이터 크기에 맞게 압축
     * @param message 메시지 객체
     * @return 압축된 바이트 배열 (null: 압축 실패)
     */
    fun compressForAdvertising(message: MeshMessage): ByteArray? {
        try {
            val bytes = message.toBytes()
            
            // 이미 크기가 작은 경우 그대로 반환
            if (bytes.size <= MAX_ADVERTISEMENT_SIZE) {
                return bytes
            }
            
            // 추가 압축 시도
            val compressed = compress(bytes, Deflater.BEST_COMPRESSION)
            
            // 압축 후에도 크기가 큰 경우 null 반환
            if (compressed.size > MAX_ADVERTISEMENT_SIZE) {
                Log.w(TAG, "Message too large for advertising: ${compressed.size} bytes")
                return null
            }
            
            return compressed
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing message for advertising", e)
            return null
        }
    }
    
    /**
     * 광고 데이터 압축 해제
     * @param bytes 압축된 바이트 배열
     * @return 원본 메시지 바이트 배열
     */
    fun decompressFromAdvertising(bytes: ByteArray): ByteArray {
        return try {
            decompress(bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error decompressing advertisement data", e)
            bytes // 압축 해제 실패 시 원본 반환
        }
    }
    
    /**
     * 바이트 배열 압축
     * @param data 압축할 바이트 배열
     * @param level 압축 레벨 (0-9)
     * @return 압축된 바이트 배열
     */
    fun compress(data: ByteArray, level: Int = Deflater.BEST_SPEED): ByteArray {
        val deflater = Deflater(level)
        val outputBuffer = ByteArray(data.size)
        
        try {
            deflater.setInput(data)
            deflater.finish()
            
            val compressedLength = deflater.deflate(outputBuffer)
            deflater.end()
            
            return outputBuffer.copyOfRange(0, compressedLength)
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing data", e)
            return data // 압축 실패 시 원본 반환
        }
    }
    
    /**
     * 바이트 배열 압축 해제
     * @param compressed 압축된 바이트 배열
     * @return 압축 해제된 바이트 배열
     */
    fun decompress(compressed: ByteArray): ByteArray {
        val inflater = Inflater()
        val outputBuffer = ByteArray(8192) // 충분한 크기로 설정
        
        try {
            inflater.setInput(compressed)
            val resultLength = inflater.inflate(outputBuffer)
            inflater.end()
            
            return outputBuffer.copyOfRange(0, resultLength)
        } catch (e: Exception) {
            Log.e(TAG, "Error decompressing data", e)
            return compressed // 압축 해제 실패 시 원본 반환
        }
    }
    
    /**
     * 큰 메시지를 GATT 패킷으로 분할
     * @param message 분할할 메시지
     * @return 분할된 패킷 목록
     */
    fun splitMessageForGatt(message: MeshMessage): List<ByteArray> {
        val messageBytes = message.toBytes()
        val result = mutableListOf<ByteArray>()
        
        try {
            var offset = 0
            while (offset < messageBytes.size) {
                val chunkSize = Math.min(MAX_GATT_PACKET_SIZE, messageBytes.size - offset)
                val chunk = messageBytes.copyOfRange(offset, offset + chunkSize)
                result.add(chunk)
                offset += chunkSize
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error splitting message for GATT", e)
        }
        
        return result
    }
    
    /**
     * 메시지 헤더 생성 (메시지 압축 및 분할에 사용)
     * @param messageId 메시지 ID
     * @param partIndex 메시지 부분 인덱스
     * @param totalParts 총 부분 수
     * @return 헤더 바이트 배열
     */
    fun createMessageHeader(messageId: UUID, partIndex: Int, totalParts: Int): ByteArray {
        val buffer = ByteBuffer.allocate(16 + 4 + 4) // UUID(16) + 파트 인덱스(4) + 총 파트 수(4)
        
        buffer.putLong(messageId.mostSignificantBits)
        buffer.putLong(messageId.leastSignificantBits)
        buffer.putInt(partIndex)
        buffer.putInt(totalParts)
        
        return buffer.array()
    }
    
    /**
     * 메시지 헤더 파싱
     * @param headerBytes 헤더 바이트 배열
     * @return Triple(메시지 ID, 메시지 부분 인덱스, 총 부분 수)
     */
    fun parseMessageHeader(headerBytes: ByteArray): Triple<UUID, Int, Int> {
        val buffer = ByteBuffer.wrap(headerBytes)
        
        val mostSigBits = buffer.getLong()
        val leastSigBits = buffer.getLong()
        val messageId = UUID(mostSigBits, leastSigBits)
        
        val partIndex = buffer.getInt()
        val totalParts = buffer.getInt()
        
        return Triple(messageId, partIndex, totalParts)
    }
    
    /**
     * 메시지 고유 키 생성 (중복 메시지 필터링에 사용)
     * @param sender 발신자 주소
     * @param sequenceNumber 시퀀스 번호
     * @return 메시지 고유 키
     */
    fun createMessageKey(sender: String, sequenceNumber: Int): String {
        return "${sender}_$sequenceNumber"
    }
    
    /**
     * 메시지 캐시 관리 (중복 메시지 필터링에 사용)
     * @param cache 캐시 맵
     * @param sender 발신자 주소
     * @param sequenceNumber 시퀀스 번호
     * @return 새로운 메시지인지 여부 (true: 새 메시지, false: 중복 메시지)
     */
    fun addToMessageCache(
        cache: MutableMap<String, MutableSet<Int>>,
        sender: String,
        sequenceNumber: Int
    ): Boolean {
        // 발신자에 대한 시퀀스 번호 집합 가져오기 (없으면 생성)
        val sequences = cache.getOrPut(sender) { mutableSetOf() }
        
        // 이미 처리한 메시지인지 확인
        if (sequences.contains(sequenceNumber)) {
            return false
        }
        
        // 새 시퀀스 번호 추가
        sequences.add(sequenceNumber)
        
        // 캐시 크기 제한
        if (sequences.size > MAX_CACHE_SIZE_PER_SENDER) {
            // 가장 오래된 시퀀스 번호 (최소값) 제거
            sequences.minOrNull()?.let { sequences.remove(it) }
        }
        
        return true
    }
    
    /**
     * 메시지가 너무 오래된 것인지 확인
     * @param timestamp 메시지 타임스탬프
     * @param maxAgeMillis 최대 허용 시간 (밀리초)
     * @return 너무 오래된 메시지인지 여부
     */
    fun isMessageTooOld(timestamp: Long, maxAgeMillis: Long = 24 * 60 * 60 * 1000): Boolean {
        val currentTime = System.currentTimeMillis()
        val age = currentTime - timestamp
        return age > maxAgeMillis
    }
} 