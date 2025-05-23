package com.ssafy.lanterns.service

import android.app.Activity
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.ssafy.lanterns.config.NeighborDiscoveryConstants
import com.ssafy.lanterns.service.ble.advertiser.NeighborAdvertiser
import com.ssafy.lanterns.service.ble.scanner.NeighborScanner
// TODO: 향후 GATT 관련 매니저 및 오디오 관련 클래스 import 추가
// import com.ssafy.lanterns.service.ble.gatt.GattClientManager
// import com.ssafy.lanterns.service.ble.gatt.GattServerManager

/**
 * 블루투스 저전력(BLE) 스캔과 광고를 관리하는 서비스
 * 앱의 백그라운드 상태에서도 BLE 작업을 계속할 수 있도록 합니다.
 * 통화 기능 관련 로직도 포함합니다.
 */
class BleService : Service() {
    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())
    private var activity: Activity? = null
    private var serverUserId: Long = -1L
    private var nickname: String = ""
    private var currentDepth: Int = 0
    private var isServiceRunning = false

    // 통화 상태 관련 변수
    enum class CallState {
        IDLE,        // 통화 없음
        OUTGOING,    // 발신 중
        INCOMING,    // 수신 중
        CONNECTED,   // 통화 연결됨
        TERMINATED   // 통화 종료됨 (일시적 상태)
    }
    private var currentCallState = CallState.IDLE
    private var currentCallTargetAddress: String? = null // 통화 대상의 BLE 주소
    private var currentCallTargetUserId: Long? = null    // 통화 대상의 서버 ID
    private var currentCallTargetNickname: String? = null // 통화 대상의 닉네임
    private var userProfileImageNumber: Int = 0 // 사용자 자신의 프로필 이미지 번호

    // TODO: GattClientManager 및 GattServerManager 인스턴스 추가
    // private lateinit var gattClientManager: GattClientManager
    // private lateinit var gattServerManager: GattServerManager

    // 통화 이벤트 리스너 (Activity 또는 ViewModel로 전달)
    interface CallEventListener {
        fun onIncomingCall(callerAddress: String, callerUserId: Long, callerNickname: String, callerProfileImageNumber: Int)
        fun onCallEstablished(targetAddress: String, targetUserId: Long, targetNickname: String)
        fun onCallTerminated(reason: String)
        fun onCallFailed(reason: String)
        fun onVoiceDataReceived(data: ByteArray) // TODO: 음성 데이터 수신 처리
        // 필요에 따라 더 많은 콜백 추가 (예: 상대방 음소거 상태 변경 등)
    }
    private var callEventListener: CallEventListener? = null

    fun setCallEventListener(listener: CallEventListener?) {
        this.callEventListener = listener
    }

    inner class LocalBinder : Binder() {
        fun getService(): BleService = this@BleService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    /**
     * 서비스를 초기화하고 시작합니다.
     */
    fun initialize(activity: Activity, serverUserId: Long, nickname: String, initialDepth: Int = 0, profileImageNumber: Int = 0) {
        this.activity = activity
        this.serverUserId = serverUserId
        this.nickname = nickname
        this.currentDepth = initialDepth
        this.userProfileImageNumber = profileImageNumber

        NeighborAdvertiser.init(activity)
        NeighborScanner.init(activity)
        NeighborScanner.setMyNickname(nickname)
        NeighborScanner.setMyServerId(serverUserId)

        // TODO: GattClientManager 및 GattServerManager 초기화
        // gattClientManager = GattClientManager(this).apply { setGattClientCallback(...) }
        // gattServerManager = GattServerManager(this).apply { setGattServerCallback(...) }

        Log.i("BleService", "서비스 초기화 완료: serverUserId=$serverUserId, nickname=\'$nickname\', initialDepth=$initialDepth, profileImageNumber=$profileImageNumber")
    }

    /**
     * BLE 스캔 및 광고를 시작합니다.
     */
    fun startBleOperations() {
        if (isServiceRunning) {
            Log.d("BleService", "BLE 작업이 이미 실행 중입니다.")
            return
        }
        if (serverUserId == -1L) {
            Log.e("BleService", "서버 ID가 설정되지 않아 BLE 작업을 시작할 수 없습니다.")
            return
        }

        isServiceRunning = true
        NeighborScanner.startScanning() // NeighborScanner는 항상 스캔 (통화 중에도 주변 탐색은 계속)
        startAdvertising() // 광고 시작 (통화 가능 상태 또는 통화 중 상태 반영)

        // 주기적으로 광고 업데이트
        scheduleNextAdvertising()

        // TODO: GATT 서버 시작 (다른 기기로부터의 연결을 받을 수 있도록)
        // gattServerManager.startServer(serverUserId, nickname)

        Log.i("BleService", "BLE 작업 시작됨 (serverUserId=$serverUserId, depth=$currentDepth)")
    }

    /**
     * BLE 스캔 및 광고를 중지합니다.
     * 통화 중일 경우에는 통화를 먼저 종료해야 합니다.
     */
    fun stopBleOperations() {
        if (!isServiceRunning) {
            Log.d("BleService", "BLE 작업이 실행 중이지 않습니다.")
            return
        }

        if (currentCallState != CallState.IDLE && currentCallState != CallState.TERMINATED) {
            Log.w("BleService", "통화($currentCallState)가 진행 중입니다. 먼저 통화를 종료해주세요.")
            // 필요시 강제 종료 로직을 여기에 추가하거나, 사용자에게 알림.
            // terminateCall("BLE 서비스 중단으로 인한 통화 종료") // 이 경우 UI 업데이트가 필요할 수 있음
        }

        handler.removeCallbacksAndMessages(null) // 예약된 광고 작업 취소
        NeighborScanner.stopScanning() // 스캐너도 중지 (선택 사항, 앱 정책에 따라 결정)
        NeighborAdvertiser.stopAdvertising()

        // TODO: GATT 서버 중지
        // gattServerManager.stopServer()

        isServiceRunning = false
        Log.i("BleService", "BLE 작업 중지됨")
    }

    /**
     * 현재 Depth 값을 업데이트합니다.
     */
    fun updateDepth(newDepth: Int) {
        if (currentDepth != newDepth) {
            currentDepth = newDepth
            if (isServiceRunning) {
                startAdvertising() // 변경된 depth로 광고 업데이트
            }
            Log.i("BleService", "Depth 업데이트됨: $newDepth")
        }
    }

    /**
     * 광고를 시작합니다. 현재 통화 상태를 반영하여 광고 내용을 조절합니다.
     */
    private fun startAdvertising() {
        if (serverUserId != -1L && activity != null) {
            val isCallable = (currentCallState == CallState.IDLE)
            // TODO: NeighborAdvertiser.startAdvertising에 isCallable 파라미터 추가 및 profileImageNumber 전달
            // 현재 NeighborAdvertiser.startAdvertising는 (Long, String, Int, Byte, Int) 시그니처를 가짐.
            // 여기에 isCallable: Boolean 파라미터를 추가해야 함.
            // NeighborAdvertiser.startAdvertising(serverUserId, nickname, currentDepth, isEmergency = 0, profileImageNumber = userProfileImageNumber, isCallable = isCallable)
            NeighborAdvertiser.startAdvertising(serverUserId, nickname, currentDepth, isEmergency = false, profileImageNumber = userProfileImageNumber) // 임시로 기존 호출 유지
            Log.d("BleService", "광고 시작 - 통화 가능: $isCallable, 프로필 이미지: $userProfileImageNumber")
        }
    }

    /**
     * 주기적인 광고 업데이트를 예약합니다.
     */
    private fun scheduleNextAdvertising() {
        handler.removeCallbacksAndMessages(null) // 기존 예약 취소
        handler.postDelayed({
            if (isServiceRunning) {
                startAdvertising()
                scheduleNextAdvertising() // 다음 광고 예약
            }
        }, NeighborDiscoveryConstants.ADVERTISE_INTERVAL_MS)
    }

    /**
     * 특정 화면으로 이동 시 BLE 작업을 일시 중지하고 재개하는 메서드
     * 예: 채팅 화면 진입 시 BLE 일시 중지, 메인 화면 복귀 시 재개
     * 통화 중 상태에 따라 BLE 작업 중지 여부 결정
     */
    fun pauseBleForScreen(screenType: String) {
        when (screenType) {
            "CHAT" -> {
                // 통화 중이 아닐 때만 BLE 광고 중지 (스캔은 계속될 수 있음)
                if (currentCallState == CallState.IDLE) {
                    NeighborAdvertiser.stopAdvertising()
                    handler.removeCallbacksAndMessages(null) // 광고 예약 취소
                    Log.i("BleService", "$screenType 화면에서 BLE 광고 중지 (통화 중 아님)")
                } else {
                    Log.i("BleService", "$screenType 화면 진입, 하지만 통화 중($currentCallState)이므로 BLE 광고/작업 유지")
                }
            }
            "MAIN" -> {
                // 메인 화면으로 돌아오면 (그리고 서비스가 실행 중이면) 광고 재개
                if (isServiceRunning) {
                    startAdvertising() // 광고 재시작
                    scheduleNextAdvertising() // 주기적 광고 다시 예약
                    Log.i("BleService", "$screenType 화면에서 BLE 광고 재개")
                }
            }
        }
    }

    // --- 통화 관련 메서드 ---
    /**
     * 특정 사용자에게 전화를 겁니다.
     * @param targetBleAddress 대상의 BLE 주소
     * @param targetUserId 대상의 서버 ID
     * @param targetNickname 대상의 닉네임
     * @param targetProfileImageNumber 대상의 프로필 이미지 번호
     */
    fun initiateCall(targetBleAddress: String, targetUserId: Long, targetNickname: String, targetProfileImageNumber: Int) {
        if (currentCallState != CallState.IDLE) {
            Log.w("BleService", "이미 통화 관련 상태($currentCallState), 새 발신 시도 무시")
            callEventListener?.onCallFailed("이미 통화 중이거나 통화 시도 중입니다.")
            return
        }
        if (activity == null) {
            Log.e("BleService", "Activity가 null이라 통화 시작 불가")
            callEventListener?.onCallFailed("서비스가 준비되지 않았습니다.")
            return
        }

        Log.i("BleService", "$targetNickname($targetUserId, 프로필:$targetProfileImageNumber)에게 전화 걸기 시도 (주소: $targetBleAddress)")
        currentCallState = CallState.OUTGOING
        currentCallTargetAddress = targetBleAddress
        currentCallTargetUserId = targetUserId
        currentCallTargetNickname = targetNickname
        // currentCallTargetProfileImageNumber = targetProfileImageNumber // 필요시 통화 대상 프로필 이미지 저장

        startAdvertising() // 광고 업데이트 (통화 중 상태로)

        // TODO: GattClientManager를 사용하여 GATT 연결 및 통화 요청
        // gattClientManager.connectAndRequestCall(
        //     targetBleAddress,
        //     serverUserId,
        //     nickname,
        //     userProfileImageNumber, // 내 프로필 이미지 번호 전달
        //     onCallEstablished = {
        //         currentCallState = CallState.CONNECTED
        //         callEventListener?.onCallEstablished(targetBleAddress, targetUserId, targetNickname)
        //         startAudioStreaming() // 음성 스트리밍 시작
        //     },
        //     onCallRejected = {
        //         terminateCall("상대방이 통화를 거절했습니다.")
        //     },
        //     onConnectionFailed = { reason ->
        //         terminateCall("연결 실패: $reason")
        //     },
        //     onVoiceDataReceived = { data ->
        //         if (currentCallState == CallState.CONNECTED) {
        //            callEventListener?.onVoiceDataReceived(data)
        //         }
        //     },
        //     onRemoteCallEnded = {
        //         terminateCall("상대방이 통화를 종료했습니다.")
        //     }
        // )
        // 임시로 성공 처리 (GATT 구현 전 테스트용)
        // handler.postDelayed({
        //     if (currentCallState == CallState.OUTGOING) { // 사용자가 취소하지 않았다면
        //         currentCallState = CallState.CONNECTED
        //         Log.i("BleService", "임시: $targetNickname 와(과) 통화 연결됨 (GATT 구현 필요)")
        //         callEventListener?.onCallEstablished(targetBleAddress, targetUserId, targetNickname)
        //     }
        // }, 3000) // 3초 후 연결된 것으로 가정
    }

    /**
     * 수신된 전화를 수락합니다.
     */
    fun acceptCall() {
        if (currentCallState != CallState.INCOMING || currentCallTargetAddress == null) {
            Log.w("BleService", "수신 대기 상태가 아니거나 대상 정보가 없어 통화 수락 불가. 현재 상태: $currentCallState")
            callEventListener?.onCallFailed("수락할 수 없는 통화 상태입니다.")
            return
        }
        Log.i("BleService", "${currentCallTargetNickname ?: "알 수 없는 발신자"}의 전화 수락")
        currentCallState = CallState.CONNECTED

        startAdvertising() // 광고 업데이트 (통화 중 상태로)

        // TODO: GattServerManager를 통해 상대방에게 "수락" 알림 전송 (내 프로필 이미지 번호 포함)
        // currentCallTargetAddress?.let { address ->
        //     gattServerManager.sendCallAccepted(address, userProfileImageNumber)
        //     callEventListener?.onCallEstablished(address, currentCallTargetUserId!!, currentCallTargetNickname!!)
        //     startAudioStreaming() // 음성 스트리밍 시작
        // }
    }

    /**
     * 수신된 전화 또는 발신 중인 전화를 거절/취소합니다.
     */
    fun rejectCall() {
        val reason: String
        if (currentCallState == CallState.INCOMING && currentCallTargetAddress != null) {
            Log.i("BleService", "${currentCallTargetNickname ?: "알 수 없는 발신자"}의 전화 거절")
            reason = "수신 거절"
            // TODO: GattServerManager를 통해 상대방에게 "거절" 알림 전송
            // currentCallTargetAddress?.let { gattServerManager.sendCallRejected(it) }
            terminateCall(reason)
        } else if (currentCallState == CallState.OUTGOING && currentCallTargetAddress != null) {
            Log.i("BleService", "${currentCallTargetNickname ?: "알 수 없는 수신자"}에게 건 전화 취소")
            reason = "발신 취소"
            // TODO: GattClientManager를 통해 "통화 취소" 알림 전송 (필요시) 및 연결 종료
            // gattClientManager.disconnect()
            terminateCall(reason)
        } else {
            Log.w("BleService", "거절/취소할 수 있는 통화 상태가 아님: $currentCallState")
            // 실패 콜백 또는 사용자에게 알림 등 추가 처리 가능
            callEventListener?.onCallFailed("거절/취소할 수 없는 통화 상태입니다.")
        }
    }

    /**
     * 현재 진행 중인 통화를 종료합니다.
     */
    fun endCall() {
        if (currentCallState != CallState.CONNECTED && currentCallState != CallState.OUTGOING && currentCallState != CallState.INCOMING) {
            Log.w("BleService", "종료할 통화가 없음. 현재 상태: $currentCallState")
            return
        }
        Log.i("BleService", "사용자 요청으로 통화 종료 시작")

        val reason = "사용자 요청으로 통화 종료"
        // TODO: 연결된 상대에게 "통화 종료" 알림 전송
        // if (currentCallState == CallState.CONNECTED) {
        //     if (gattClientManager.isConnectedTo(currentCallTargetAddress)) { // 내가 건 전화인 경우
        //         gattClientManager.sendCallEndCommand()
        //     } else if (currentCallTargetAddress != null && gattServerManager.isClientConnected(currentCallTargetAddress!!)) { // 내가 받은 전화인 경우
        //         gattServerManager.sendCallEndCommand(currentCallTargetAddress!!)
        //     }
        // }
        terminateCall(reason)
    }

    /**
     * 통화를 내부적으로 종료하고 상태를 초기화합니다.
     * @param reason 통화 종료 사유
     */
    private fun terminateCall(reason: String) {
        Log.i("BleService", "통화 종료 처리 시작. 사유: $reason. 현재 상태: $currentCallState, 대상: ${currentCallTargetNickname}")
        if (currentCallState == CallState.IDLE || currentCallState == CallState.TERMINATED) {
            Log.d("BleService", "이미 통화가 없거나 종료 처리 중입니다.")
            return // 이미 종료되었거나 유휴 상태면 아무것도 안함
        }

        val previousState = currentCallState
        val previousTargetInfo = Triple(currentCallTargetAddress, currentCallTargetUserId, currentCallTargetNickname)

        currentCallState = CallState.TERMINATED // 상태를 먼저 변경하여 중복 호출 방지

        // TODO: 오디오 스트리밍 중지
        // stopAudioStreaming()

        // TODO: GATT 연결 해제
        // if (previousTargetInfo.first != null && gattClientManager.isConnectedTo(previousTargetInfo.first!!)) {
        //     gattClientManager.disconnect()
        // }
        // previousTargetInfo.first?.let {
        //     if (gattServerManager.isClientConnected(it)) {
        //         gattServerManager.disconnectClient(it)
        //     }
        // }

        callEventListener?.onCallTerminated(reason)

        // 상태 초기화 및 광고 업데이트 예약
        // 약간의 딜레이 후 IDLE 상태로 변경하고 광고 재시작 (통화 종료 메시지 전송 시간 확보 등)
        handler.postDelayed({
            currentCallState = CallState.IDLE
            currentCallTargetAddress = null
            currentCallTargetUserId = null
            currentCallTargetNickname = null
            Log.d("BleService", "통화 상태 IDLE로 완전 전환, 광고 통화 가능으로 변경")
            if (isServiceRunning) {
                startAdvertising() // 통화 가능 상태로 광고 다시 시작
                scheduleNextAdvertising() // 주기적 광고 다시 예약 (startAdvertising 내에서 호출되므로 중복될 수 있음, 확인 필요)
            }
        }, 500) // 0.5초 딜레이
    }

    // TODO: 음성 스트리밍 시작/중지 메서드 (AudioRecord, AudioTrack 관련)
    // private fun startAudioStreaming() {
    //     Log.d("BleService", "음성 스트리밍 시작")
    //     // 1. AudioRecord 초기화 및 녹음 시작
    //     // 2. 녹음된 데이터를 주기적으로 GattClient/Server Manager를 통해 전송
    //     // 3. AudioTrack 초기화 (수신 데이터 재생용)
    // }
    // private fun stopAudioStreaming() {
    //     Log.d("BleService", "음성 스트리밍 중지")
    //     // 1. AudioRecord 중지 및 릴리스
    //     // 2. AudioTrack 중지 및 릴리스
    // }

    // TODO: 외부(Gatt Managers)에서 호출될 메서드들
    /** GattServerManager가 외부에서 통화 요청을 받았을 때 호출 */
    // fun onExternalCallRequest(deviceAddress: String, userId: Long, userNickname: String, userProfileImageNumber: Int) {
    //     Log.d("BleService", "외부 통화 요청 수신: $userNickname ($userId, 프로필:$userProfileImageNumber) from $deviceAddress")
    //     if (currentCallState == CallState.IDLE) {
    //         currentCallState = CallState.INCOMING
    //         currentCallTargetAddress = deviceAddress
    //         currentCallTargetUserId = userId
    //         currentCallTargetNickname = userNickname
    //         // currentCallTargetProfileImageNumber = userProfileImageNumber // 상대방 프로필 번호 저장
    //         startAdvertising() // 광고 업데이트 (통화 중 상태로)
    //         callEventListener?.onIncomingCall(deviceAddress, userId, userNickname, userProfileImageNumber)
    //     } else {
    //         Log.w("BleService", "이미 통화 관련 상태($currentCallState), 새 통화 요청 무시. 상대에게 '통화 중' 응답 전송 필요.")
    //         // TODO: 상대방에게 "통화 중(BUSY)" 응답 전송
    //         // gattServerManager.sendBusyResponse(deviceAddress)
    //     }
    // }

    /** GattClient/ServerManager가 외부에서 통화 종료 신호를 받았을 때 호출 */
    // fun onExternalCallEnded(deviceAddress: String, reason: String = "상대방이 통화를 종료했습니다.") {
    //     if (currentCallTargetAddress == deviceAddress && (currentCallState == CallState.CONNECTED || currentCallState == CallState.OUTGOING || currentCallState == CallState.INCOMING)) {
    //         Log.i("BleService", "외부로부터 통화 종료됨 from $deviceAddress. 이유: $reason")
    //         terminateCall(reason)
    //     }
    // }

    /** GattClient/ServerManager가 음성 데이터를 받았을 때 호출 */
    // fun onExternalVoiceDataReceived(deviceAddress: String, data: ByteArray) {
    //     if (currentCallState == CallState.CONNECTED && currentCallTargetAddress == deviceAddress) {
    //         callEventListener?.onVoiceDataReceived(data)
    //     }
    // }


    // --- Getter 메서드 ---
    fun getCurrentCallState(): CallState = currentCallState
    fun getCurrentCallTargetInfo(): Triple<String?, Long?, String?> {
        return Triple(currentCallTargetAddress, currentCallTargetUserId, currentCallTargetNickname)
    }
    fun getUserProfileImageNumber(): Int = userProfileImageNumber


    override fun onDestroy() {
        Log.i("BleService", "서비스 onDestroy 시작")
        if (currentCallState != CallState.IDLE && currentCallState != CallState.TERMINATED) {
            terminateCall("서비스 종료")
        }
        stopBleOperations() // 핸들러 콜백 제거 등 (isServiceRunning = false 포함)

        // TODO: GattClientManager 및 GattServerManager 리소스 해제
        // gattClientManager.release()
        // gattServerManager.release()

        activity = null
        callEventListener = null
        // isServiceRunning = false // stopBleOperations 내부에서 처리됨
        Log.i("BleService", "서비스 onDestroy 완료")
        super.onDestroy()
    }
}