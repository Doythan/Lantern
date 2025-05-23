package com.ssafy.lanterns.data.source.ble.mesh

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * 메쉬 네트워크 메시지 클래스
 * 메쉬 네트워크를 통해 전송되는 메시지의 구조 정의
 */
data class MeshMessage(
    val sender: String,                   // 발신자 주소
    val senderNickname: String,           // 발신자 닉네임
    val sequenceNumber: Int,              // 시퀀스 번호 (중복 메시지 필터링용)
    val messageType: MessageType,         // 메시지 유형
    val content: String,                  // 메시지 내용
    val timestamp: Long,                  // 타임스탬프
    val ttl: Int,                         // Time To Live (홉 수)
    val target: String? = null            // 대상 주소 (null: 브로드캐스트)
) {
    companion object {
        private const val TAG = "MeshMessage"
        private val gson = Gson()
        
        /**
         * 바이트 배열에서 메시지 객체로 변환
         * @param bytes 바이트 배열
         * @return 메시지 객체 또는 null (파싱 실패 시)
         */
        fun fromBytes(bytes: ByteArray): MeshMessage? {
            return try {
                val decompressedBytes = decompress(bytes)
                val json = String(decompressedBytes, StandardCharsets.UTF_8)
                gson.fromJson(json, MeshMessage::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Error deserializing message", e)
                null
            }
        }
        
        /**
         * JSON 문자열에서 메시지 객체로 변환
         * @param json JSON 문자열
         * @return 메시지 객체 또는 null (파싱 실패 시)
         */
        fun fromJson(json: String): MeshMessage? {
            return try {
                gson.fromJson(json, MeshMessage::class.java)
            } catch (e: JsonSyntaxException) {
                Log.e(TAG, "Error parsing JSON", e)
                null
            }
        }
        
        /**
         * 압축 해제
         * @param compressed 압축된 바이트 배열
         * @return 압축 해제된 바이트 배열
         */
        private fun decompress(compressed: ByteArray): ByteArray {
            val inflater = Inflater()
            val outputBuffer = ByteArray(8192) // 최대 크기
            
            return try {
                inflater.setInput(compressed)
                val resultLength = inflater.inflate(outputBuffer)
                inflater.end()
                
                outputBuffer.copyOfRange(0, resultLength)
            } catch (e: Exception) {
                Log.e(TAG, "Error decompressing data", e)
                compressed // 압축 해제 실패 시 원본 반환
            }
        }
    }
    
    /**
     * 메시지 객체를 바이트 배열로 변환
     * @return 바이트 배열
     */
    fun toBytes(): ByteArray {
        val json = gson.toJson(this)
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        return compress(bytes)
    }
    
    /**
     * 메시지 객체를 JSON 문자열로 변환
     * @return JSON 문자열
     */
    fun toJson(): String {
        return gson.toJson(this)
    }
    
    /**
     * 압축
     * @param data 압축할 바이트 배열
     * @return 압축된 바이트 배열
     */
    private fun compress(data: ByteArray): ByteArray {
        val deflater = Deflater()
        val outputBuffer = ByteArray(data.size * 2) // 충분한 크기로 설정
        
        return try {
            deflater.setInput(data)
            deflater.finish()
            
            val compressedLength = deflater.deflate(outputBuffer)
            deflater.end()
            
            outputBuffer.copyOfRange(0, compressedLength)
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing data", e)
            data // 압축 실패 시 원본 반환
        }
    }
}

// NearbyNode 클래스는 ChatMessage.kt에 정의되어 있으므로 여기서는 제거합니다.
// import com.ssafy.lanterns.data.source.ble.mesh.NearbyNode를 사용하여 ChatMessage.kt의 클래스 참조 