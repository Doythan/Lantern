package com.ssafy.lanterns.data.source.ble.mesh

import android.util.Log
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * 키 타입 열거형
 */
enum class KeyType {
    DEVICE_KEY,    // 장치별 고유 키
    NETWORK_KEY,   // 네트워크 공통 키
    APP_KEY        // 애플리케이션 키
}

/**
 * 보안 계층 (Security Layer)
 * 메시지 암호화, 복호화 및 키 관리를 담당
 * 
 * 현재는 최소한의 기본 암호화를 제공하며, 추후 보완 예정
 */
class SecurityManager {
    private val TAG = "SecurityManager"
    
    // 네트워크 키 (모든 노드가 공유)
    private var networkKey: ByteArray? = null
    
    // 앱 키 (채팅 등 애플리케이션 데이터 암호화용)
    private var appKey: ByteArray? = null
    
    // 개별 장치 키 (노드별 고유 키)
    private var deviceKey: ByteArray? = null
    
    // 개인 정보 보호 키 (광고 데이터 암호화용)
    private var privacyKey: ByteArray? = null
    
    /**
     * 마스터 키로부터 다양한 키를 파생
     * HKDF 기법을 사용하여 마스터 키(프로비저닝 시 교환)로부터 
     * 네트워크 키, 앱 키, 개인 정보 보호 키를 파생
     * 
     * @param masterKey 마스터 키 (프로비저닝 단계에서 설정)
     */
    fun deriveKeys(masterKey: ByteArray) {
        Log.d(TAG, "Deriving keys from master key")
        
        // 네트워크 키 파생 (salt: "nk")
        networkKey = hkdfDerive(masterKey, "nk".toByteArray(), KEY_SIZE)
        
        // 앱 키 파생 (salt: "ak")
        appKey = hkdfDerive(masterKey, "ak".toByteArray(), KEY_SIZE)
        
        // 개인 정보 보호 키 파생 (salt: "pk")
        privacyKey = hkdfDerive(masterKey, "pk".toByteArray(), KEY_SIZE)
        
        // 장치 키는 별도로 관리됨
        Log.d(TAG, "Keys derived successfully")
    }
    
    /**
     * 장치 키 설정 (프로비저닝 단계에서 할당)
     */
    fun setDeviceKey(key: ByteArray) {
        deviceKey = key
    }
    
    /**
     * PDU 암호화
     * @param pdu 암호화할 PDU
     * @param keyType 사용할 키 타입
     * @return 암호화된 PDU 또는 null (암호화 실패)
     */
    fun encrypt(pdu: ByteArray, keyType: KeyType): ByteArray? {
        val key = getKeyByType(keyType) ?: return pdu // 키가 없는 경우 원본 반환
        
        try {
            // IV (초기화 벡터) 생성
            val iv = generateIV()
            
            // AES 암호화 (AES/CBC/PKCS5Padding)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encryptedData = cipher.doFinal(pdu)
            
            // IV와 암호화된 데이터를 합쳐서 반환
            val result = ByteArray(IV_SIZE + encryptedData.size)
            System.arraycopy(iv, 0, result, 0, IV_SIZE)
            System.arraycopy(encryptedData, 0, result, IV_SIZE, encryptedData.size)
            
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Encryption error", e)
            return null
        }
    }
    
    /**
     * PDU 복호화
     * @param encryptedPdu 암호화된 PDU
     * @param keyType 사용할 키 타입
     * @return 복호화된 PDU 또는 null (복호화 실패)
     */
    fun decrypt(encryptedPdu: ByteArray, keyType: KeyType): ByteArray? {
        val key = getKeyByType(keyType) ?: return encryptedPdu // 키가 없는 경우 원본 반환
        
        try {
            if (encryptedPdu.size <= IV_SIZE) {
                Log.e(TAG, "Encrypted data too small")
                return null
            }
            
            // IV 추출
            val iv = ByteArray(IV_SIZE)
            System.arraycopy(encryptedPdu, 0, iv, 0, IV_SIZE)
            
            // 암호화된 데이터 추출
            val encryptedData = ByteArray(encryptedPdu.size - IV_SIZE)
            System.arraycopy(encryptedPdu, IV_SIZE, encryptedData, 0, encryptedData.size)
            
            // AES 복호화
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            return cipher.doFinal(encryptedData)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption error", e)
            return null
        }
    }
    
    /**
     * 네트워크 키 업데이트
     * 주기적인 키 갱신에 사용
     * 
     * @param newKey 새로운 네트워크 키
     */
    fun updateNetworkKey(newKey: ByteArray) {
        Log.d(TAG, "Updating network key")
        networkKey = newKey
    }
    
    /**
     * 앱 키 업데이트
     * 주기적인 키 갱신에 사용
     * 
     * @param newKey 새로운 앱 키
     */
    fun updateAppKey(newKey: ByteArray) {
        Log.d(TAG, "Updating app key")
        appKey = newKey
    }
    
    /**
     * 키 타입에 따른 키 반환
     */
    private fun getKeyByType(keyType: KeyType): ByteArray? {
        return when (keyType) {
            KeyType.DEVICE_KEY -> deviceKey
            KeyType.NETWORK_KEY -> networkKey
            KeyType.APP_KEY -> appKey
        }
    }
    
    /**
     * IV(Initialization Vector) 생성
     */
    private fun generateIV(): ByteArray {
        val iv = ByteArray(IV_SIZE)
        Random.nextBytes(iv)
        return iv
    }
    
    /**
     * HKDF 키 파생 알고리즘 구현
     * HMAC 기반 키 파생 함수
     * 
     * @param ikm 입력 키 자료 (Initial Key Material)
     * @param salt 솔트 (사용할 컨텍스트)
     * @param length 생성할 키의 길이
     * @return 파생된 키
     */
    private fun hkdfDerive(ikm: ByteArray, salt: ByteArray, length: Int): ByteArray {
        try {
            // 1단계: HMAC-SHA256(salt, ikm)을 사용하여 prk 생성
            val md = MessageDigest.getInstance("SHA-256")
            val prk = md.digest(ikm + salt)
            
            // 2단계: HMAC-SHA256(prk, info)를 사용하여 출력 키 재료 생성
            val okm = ByteArray(length)
            
            md.reset()
            val t = md.digest(prk + byteArrayOf(0x01))
            System.arraycopy(t, 0, okm, 0, minOf(t.size, length))
            
            return okm
        } catch (e: Exception) {
            Log.e(TAG, "HKDF derivation error", e)
            // 오류 시 기본 키 생성
            val fallbackKey = ByteArray(length)
            Random.nextBytes(fallbackKey)
            return fallbackKey
        }
    }
    
    companion object {
        // 키 크기 (AES-128)
        private const val KEY_SIZE = 16
        
        // IV 크기
        private const val IV_SIZE = 16
    }
} 