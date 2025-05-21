package com.ssafy.lanterns.data.source.ble

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.ssafy.lanterns.data.source.ble.advertiser.AudioAdvertiserManager
import com.ssafy.lanterns.data.source.ble.audio.AudioManager
import com.ssafy.lanterns.data.source.ble.gatt.AudioGattClientManager
import com.ssafy.lanterns.data.source.ble.gatt.AudioGattServerManager
import com.ssafy.lanterns.data.source.ble.scanner.AudioScannerManager
import com.ssafy.lanterns.ui.screens.call.CallState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioAdvertiser: AudioAdvertiserManager by lazy {
        AudioAdvertiserManager(context)
    }

    private val audioGattServer: AudioGattServerManager by lazy {
        AudioGattServerManager(
            context,
            { address, data -> notifyDataReceived(address, data) },
            { address -> notifyClientConnected(address) },
            { address -> notifyClientDisconnected(address) }
        )
    }

    private val audioGattClient: AudioGattClientManager by lazy {
        AudioGattClientManager(
            context,
            { address, data -> notifyDataReceived(address, data) },
            { address -> notifyClientConnected(address) },
            { address -> notifyClientDisconnected(address) }
        )
    }

    private val audioScanner: AudioScannerManager by lazy {
        AudioScannerManager(context) { device ->
            // 주변 기기를 스캔했을 때 자동으로 연결하지 않음
        }
    }

    private val listeners = ConcurrentHashMap<String, BleEventListener>()

    fun initialize() {
        try {
            // 모든 기기가 서버 역할을 함 (양방향 통신을 위해)
            audioAdvertiser.startAdvertising()
            audioGattServer.openGattServer()

            // 스캐닝도 주기적으로 진행하도록 수정
            audioScanner.enablePeriodicScanning()

            Log.d(TAG, "BLE 서비스 초기화 성공 - 양방향 모드")
        } catch (e: Exception) {
            Log.e(TAG, "BLE 서비스 초기화 실패", e)
        }
    }

    fun registerListener(key: String, listener: BleEventListener) {
        listeners[key] = listener
        Log.d(TAG, "리스너 등록: $key, 총 리스너 수: ${listeners.size}")
    }

    private var preserveCallViewModelListener = false

    fun setPreserveCallViewModelListener(preserve: Boolean) {
        preserveCallViewModelListener = preserve
        Log.d(TAG, "CallViewModel 리스너 보존 모드 설정: $preserve")
    }

    fun unregisterListener(key: String) {
        if (preserveCallViewModelListener && key == "CallViewModel") {
            Log.d(TAG, "통화 중에는 CallViewModel 리스너를 제거하지 않음 (보존 모드 활성화)")
            return
        }

        // CallViewModel 리스너 제거 시 중요 로그 추가
        if (key == "CallViewModel") {
            Log.d(TAG, "CallViewModel 리스너 제거 요청됨", Exception("디버깅용 스택 추적"))
            // 통화 중이라면 강제로 보존 모드 활성화
            if (listeners.containsKey("CallViewModel")) {
                setPreserveCallViewModelListener(true)
                setPreventDisconnectDuringCall(true)
                return
            }
        }

        listeners.remove(key)
        Log.d(TAG, "리스너 제거: $key, 남은 리스너 수: ${listeners.size}")
    }

    fun startScanning() {
        Log.d(TAG, "BLE 스캔 시작")
        audioScanner.startScanning()
    }

    fun stopScanning() {
        Log.d(TAG, "BLE 스캔 중지")
        audioScanner.stopScanning()
    }

    fun getDiscoveredDevices(): Set<String> {
        val devices = audioScanner.getDiscoveredDevices()
        Log.d(TAG, "발견된 기기 수: ${devices.size}")
        return devices
    }

    fun getBluetoothDevice(address: String) = audioScanner.getBluetoothDevice(address)

    fun connectToDevice(deviceAddress: String) {
        Log.d(TAG, "기기 연결 시도: $deviceAddress")
        val device = audioScanner.getBluetoothDevice(deviceAddress)
        if (device == null) {
            Log.e(TAG, "기기 연결 실패: 기기를 찾을 수 없음 - $deviceAddress")
            return
        }

        // 양방향 연결
        audioGattClient.connectToDevice(device)
        Log.d(TAG, "기기 연결 요청 완료: $deviceAddress")
    }

    fun isConnected(address: String): Boolean {
        val connected = audioGattClient.isConnected(address)
        Log.d(TAG, "기본 연결 상태 확인: $address - $connected")
        return connected
    }

    fun isFullyConnected(address: String): Boolean {
        val fullyConnected = audioGattClient.isFullyConnected(address)
        Log.d(TAG, "완전 연결 상태 확인: $address - $fullyConnected")
        return fullyConnected
    }

    fun sendAudioData(data: ByteArray) {
        if (data.isEmpty()) {
            Log.e(TAG, "sendAudioData: 빈 데이터 전송 시도")
            return
        }

        // 중요한 메시지만 로깅 (오디오 데이터 제외)
        if (data[0] != AudioManager.TYPE_AUDIO_DATA &&
            data[0] != AudioManager.TYPE_HEARTBEAT &&
            data[0] != AudioManager.TYPE_HEARTBEAT_ACK) {

            val typeString = when (data[0]) {
                AudioManager.TYPE_CALL_REQUEST -> "통화 요청"
                AudioManager.TYPE_CALL_ACCEPT -> "통화 수락"
                AudioManager.TYPE_CALL_END -> "통화 종료"
                else -> "알 수 없음(${data[0]})"
            }
            Log.d(TAG, "sendAudioData: $typeString 메시지 전송 시도")
        }

        // 클라이언트 연결을 통해 데이터 전송
        try {
            val clients = audioGattClient.getConnectedDevices()
            if (clients.isNotEmpty()) {
                // 통화 수락 메시지인 경우 여러 번 전송
                if (data[0] == AudioManager.TYPE_CALL_ACCEPT) {
                    for (i in 0 until 3) {
                        try {
                            Log.d(TAG, "통화 수락 메시지 전송 시도 ${i+1}/3 (클라이언트)")
                            audioGattClient.sendAudioData(data)
                            Thread.sleep(300)
                        } catch (e: Exception) {
                            Log.e(TAG, "통화 수락 메시지 전송 실패(${i+1}/3)", e)
                        }
                    }
                } else {
                    // 일반 메시지 전송
                    audioGattClient.sendAudioData(data)

                    // 주기적으로 연결 상태 로깅
                    if (data[0] == AudioManager.TYPE_AUDIO_DATA &&
                        SystemClock.elapsedRealtime() % 5000 < 30) {
                        Log.d(TAG, "오디오 데이터 전송: ${clients.size}개 기기에 전송 중")
                    }
                }
            } else {
                if (data[0] != AudioManager.TYPE_AUDIO_DATA) {
                    Log.d(TAG, "연결된 클라이언트가 없음")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "클라이언트 메시지 전송 오류", e)
        }
    }

    fun broadcastAudioData(data: ByteArray) {
        if (data.isEmpty()) {
            Log.e(TAG, "broadcastAudioData: 빈 데이터 전송 시도")
            return
        }

        // 오디오 데이터와 통화 수락 관련 로깅 개선
        if (data.isNotEmpty()) {
            val isAudioData = data[0] == AudioManager.TYPE_AUDIO_DATA
            val isHeartbeat = data[0] == AudioManager.TYPE_HEARTBEAT || data[0] == AudioManager.TYPE_HEARTBEAT_ACK

            if (!isAudioData && !isHeartbeat) {
                val typeString = when (data[0]) {
                    AudioManager.TYPE_CALL_REQUEST -> "통화 요청"
                    AudioManager.TYPE_CALL_ACCEPT -> "통화 수락"
                    AudioManager.TYPE_CALL_END -> "통화 종료"
                    else -> "알 수 없음(${data[0]})"
                }
                Log.d(TAG, "broadcastAudioData: $typeString 메시지 서버로 브로드캐스트")
            }
        }

        // 통화 수락 메시지인 경우 특별 처리 (3회 전송)
        if (data.isNotEmpty() && data[0] == AudioManager.TYPE_CALL_ACCEPT) {
            Log.d(TAG, "통화 수락 메시지 - 서버 브로드캐스트 3회 시도")
            val serverClients = audioGattServer.getConnectedClients()

            if (serverClients.isNotEmpty()) {
                for (i in 0 until 3) {
                    try {
                        Log.d(TAG, "통화 수락 메시지 서버 브로드캐스트 시도 ${i+1}/3")
                        audioGattServer.broadcastAudioData(data)
                        Thread.sleep(300)
                    } catch (e: Exception) {
                        Log.e(TAG, "통화 수락 메시지 서버 브로드캐스트 실패(${i+1}/3)", e)
                    }
                }
            } else {
                Log.d(TAG, "통화 수락 메시지 서버 브로드캐스트 실패: 연결된 클라이언트 없음")
            }
            return
        }

        // 일반 메시지 브로드캐스트
        try {
            val serverClients = audioGattServer.getConnectedClients()
            if (serverClients.isNotEmpty()) {
                if (data[0] == AudioManager.TYPE_AUDIO_DATA && SystemClock.elapsedRealtime() % 5000 < 30) {
                    Log.d(TAG, "서버 연결을 통해 ${serverClients.size}개 기기에 오디오 데이터 브로드캐스트")
                }
                audioGattServer.broadcastAudioData(data)
            } else if (data[0] != AudioManager.TYPE_AUDIO_DATA && data[0] != AudioManager.TYPE_HEARTBEAT &&
                data[0] != AudioManager.TYPE_HEARTBEAT_ACK) {
                Log.d(TAG, "서버에 연결된 클라이언트가 없음, 브로드캐스트 실패")
            }
        } catch (e: Exception) {
            if (data[0] != AudioManager.TYPE_AUDIO_DATA && data[0] != AudioManager.TYPE_HEARTBEAT &&
                data[0] != AudioManager.TYPE_HEARTBEAT_ACK) {
                Log.e(TAG, "서버 브로드캐스트 오류", e)
            }
        }
    }

    private var preventDisconnectDuringCall = false

    fun setPreventDisconnectDuringCall(prevent: Boolean) {
        preventDisconnectDuringCall = prevent
        Log.d(TAG, "통화 중 연결 해제 방지 모드 설정: $prevent")
    }

    fun disconnectAll() {
        // 통화 중에는 연결 해제 방지

        if (preventDisconnectDuringCall || listeners.containsKey("CallViewModel")) {
            Log.d(TAG, "통화 중에는 모든 기기 연결 해제 동작 수행하지 않음")
            return
        }

        Log.d(TAG, "모든 기기 연결 해제")
        audioGattClient.disconnectAll()
    }

    fun checkConnectionStatus() {
        Log.d(TAG, "연결 상태 확인 시작")
        audioGattServer.checkServerStatus()
        audioGattClient.checkConnectionStatus()
        audioAdvertiser.checkAdvertisingStatus()
        Log.d(TAG, "연결 상태 확인 완료")
    }

    fun enablePeriodicScanning() {
        Log.d(TAG, "주기적 스캔 활성화")
        audioScanner.enablePeriodicScanning()
    }

    fun disablePeriodicScanning() {
        Log.d(TAG, "주기적 스캔 비활성화")
        audioScanner.disablePeriodicScanning()
    }

    private fun notifyDataReceived(address: String, data: ByteArray) {
        if (data.isNotEmpty()) {
            val typeString = when (data[0]) {
                AudioManager.TYPE_CALL_REQUEST -> "통화 요청"
                AudioManager.TYPE_CALL_ACCEPT -> "통화 수락"
                AudioManager.TYPE_CALL_END -> "통화 종료"
                AudioManager.TYPE_AUDIO_DATA -> "오디오 데이터"
                AudioManager.TYPE_HEARTBEAT -> "하트비트"
                AudioManager.TYPE_HEARTBEAT_ACK -> "하트비트 응답"
                else -> "알 수 없음(${data[0]})"
            }

            // 오디오 데이터와 하트비트는 너무 많으므로 로그 생략
            if (data[0] != AudioManager.TYPE_AUDIO_DATA &&
                data[0] != AudioManager.TYPE_HEARTBEAT &&
                data[0] != AudioManager.TYPE_HEARTBEAT_ACK) {
                Log.d(TAG, "데이터 수신: $typeString (from: $address)")
            }
        }
        listeners.values.forEach { it.onDataReceived(address, data) }
    }

    private fun notifyClientConnected(address: String) {
        Log.d(TAG, "클라이언트 연결됨: $address")
        listeners.values.forEach { it.onDeviceConnected(address) }
    }

    private fun notifyClientDisconnected(address: String) {
        Log.d(TAG, "클라이언트 연결 해제됨: $address")
        listeners.values.forEach { it.onDeviceDisconnected(address) }
    }

    companion object {
        private const val TAG = "BleManager"
    }

    interface BleEventListener {
        fun onDataReceived(address: String, data: ByteArray)
        fun onDeviceConnected(address: String)
        fun onDeviceDisconnected(address: String)
    }
}