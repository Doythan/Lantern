package com.ssafy.lantern.ui.view.audio

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ssafy.lantern.R
import com.ssafy.lantern.data.source.ble.advertiser.AudioAdvertiserManager
import com.ssafy.lantern.data.source.ble.audio.AudioManager
import com.ssafy.lantern.data.source.ble.gatt.AudioGattClientManager
import com.ssafy.lantern.data.source.ble.gatt.AudioGattServerManager
import com.ssafy.lantern.data.source.ble.scanner.AudioScannerManager
import com.ssafy.lantern.utils.PermissionHelper
import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusDecoder
import io.github.jaredmdobson.concentus.OpusEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val SAMPLE_RATE = 24_000
private const val MAX_CALL_REQUEST_TIMEOUT = 30_000L
private const val CONNECTION_CHECK_INTERVAL = 10_000L

class AudioActivity : AppCompatActivity() {
    private lateinit var permHelper: PermissionHelper
    private lateinit var advertiser: AudioAdvertiserManager
    private lateinit var scanner: AudioScannerManager
    private lateinit var gattServer: AudioGattServerManager
    private lateinit var gattClient: AudioGattClientManager
    private lateinit var btnCall: Button
    private lateinit var tvStatus: TextView
    private var recorder: AudioRecord? = null
    private var sendThread: Thread? = null
    private val handler = Handler(Looper.getMainLooper())

    private var inCall = false
    private var isRequesting = false
    private var connectedDevice: String? = null
    private var callRequestTimeoutRunnable: Runnable? = null

    private var streamTrack: AudioTrack? = null

    private lateinit var opusEnc: OpusEncoder
    private lateinit var opusDec: OpusDecoder

    private var lastHeartbeatTime = 0L
    private var heartbeatMissCount = 0
    private val MAX_HEARTBEAT_MISS = 3

    private lateinit var bluetoothEnableLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionsLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_audio)

        bluetoothEnableLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                initBle()
            } else {
                toast("블루투스가 활성화되지 않았습니다.")
                updateStatusText("블루투스 비활성화 상태")
            }
        }

        permissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                startBle()
            } else {
                toast("권한 거부됨")
                updateStatusText("필수 권한 없음")
            }
        }

        btnCall = findViewById(R.id.recordAudioButton)
        tvStatus = findViewById(R.id.statusTextView)

        permHelper = PermissionHelper(this)

        gattClient = AudioGattClientManager(
            this,
            { address, data -> handleRx(address, data) },
            { address -> handleClientConnected(address) },
            { address -> handleClientDisconnected(address) }
        )

        gattServer = AudioGattServerManager(
            this,
            { address, data -> handleRx(address, data) },
            { address -> handleServerClientConnected(address) },
            { address -> handleServerClientDisconnected(address) }
        )

        advertiser = AudioAdvertiserManager(this)
        scanner = AudioScannerManager(this) { dev -> gattClient.connectToDevice(dev) }

        opusEnc = OpusEncoder(SAMPLE_RATE, 1, OpusApplication.OPUS_APPLICATION_AUDIO)
        opusDec = OpusDecoder(SAMPLE_RATE, 1)

        initBle()
        setupUI()
        startConnectionMonitoring()
    }

    private fun setupUI() {
        btnCall.setOnClickListener { toggleCall() }
        updateStatusText("초기화 중...")
    }

    private fun initBle() {
        if (!permHelper.isBluetoothEnabled()) {
            permHelper.requestEnableBluetooth(bluetoothEnableLauncher)
            updateStatusText("블루투스 활성화 필요")
        } else if (permHelper.hasPermission()) {
            startBle()
        } else {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.RECORD_AUDIO
            )
            permissionsLauncher.launch(permissions)
            updateStatusText("권한 요청 중...")
        }
    }

    private fun startBle() {
        try {
            advertiser.startAdvertising()
            gattServer.openGattServer()
            scanner.startScanning()
            scanner.enablePeriodicScanning()
            updateStatusText("BLE 스캔·광고 시작")
            toast("BLE 스캔·광고 시작")
        } catch (e: Exception) {
            updateStatusText("BLE 시작 실패: ${e.message}")
            handler.postDelayed({ initBle() }, 3000)
        }
    }

    private fun startConnectionMonitoring() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                checkConnectionStatus()
                if (connectedDevice != null) {
                    sendHeartbeat()
                }
                handler.postDelayed(this, CONNECTION_CHECK_INTERVAL)
            }
        }, CONNECTION_CHECK_INTERVAL)
    }

    private fun checkConnectionStatus() {
        gattServer.checkServerStatus()
        gattClient.checkConnectionStatus()
        advertiser.checkAdvertisingStatus()
        val clientDevices = gattClient.getConnectedDevices()
        val serverClients = gattServer.getConnectedClients()
        updateConnectionStatus(clientDevices, serverClients)
    }

    private fun sendHeartbeat() {
        connectedDevice?.let { _ ->
            try {
                gattClient.sendAudioData(byteArrayOf(AudioManager.TYPE_HEARTBEAT))
                lastHeartbeatTime = SystemClock.elapsedRealtime()
            } catch (e: Exception) {
                // Heartbeat send failure
            }
        }
    }

    private fun handleHeartbeatAck(address: String) {
        heartbeatMissCount = 0
    }

    private fun handleHeartbeat(address: String) {
        try {
            gattClient.sendAudioData(byteArrayOf(AudioManager.TYPE_HEARTBEAT_ACK))
        } catch (e: Exception) {
            // Heartbeat ACK send failure
        }
    }

    private fun handleClientConnected(address: String) {
        connectedDevice = address
        updateStatusText("연결됨: $address")
    }

    private fun handleClientDisconnected(address: String) {
        clearCallRequestTimeout()
        if (connectedDevice == address) {
            connectedDevice = null
            if (inCall) {
                stopDuplex()
                runOnUiThread {
                    btnCall.text = "통화 시작"
                    updateStatusText("연결 끊김 (통화 중단)")
                    if (!isFinishing) toast("연결 끊김으로 통화가 종료되었습니다.")
                }
            } else if (isRequesting) {
                isRequesting = false
                runOnUiThread {
                    btnCall.text = "통화 시작"
                    updateStatusText("연결 끊김 (요청 취소)")
                    if (!isFinishing) toast("요청 중 연결이 끊겼습니다.")
                }
            } else {
                runOnUiThread {
                    updateStatusText("연결 해제됨: $address")
                }
            }
        }
    }

    private fun handleServerClientConnected(address: String) {
        updateStatusText("클라이언트 연결됨: $address")
    }

    private fun handleServerClientDisconnected(address: String) {
        updateStatusText("클라이언트 연결 해제됨")
    }

    private fun updateConnectionStatus(clientDevices: List<String>, serverClients: List<String>) {
        runOnUiThread {
            val status = StringBuilder("연결 상태:\n")
            if (clientDevices.isEmpty() && serverClients.isEmpty()) {
                status.append("연결된 기기 없음")
            } else {
                if (clientDevices.isNotEmpty()) {
                    status.append("클라이언트 연결 (${clientDevices.size}):\n")
                    clientDevices.forEach {
                        val isFullyConnected = gattClient.isFullyConnected(it)
                        status.append("- $it ${if(isFullyConnected) "✓" else "?"}\n")
                    }
                }
                if (serverClients.isNotEmpty()) {
                    status.append("서버 클라이언트 (${serverClients.size}):\n")
                    serverClients.forEach {
                        val isSubscribed = gattServer.isClientConnected(it)
                        status.append("- $it ${if(isSubscribed) "✓" else "?"}\n")
                    }
                }
            }
            tvStatus.text = status.toString()
        }
    }

    private fun toggleCall() {
        when {
            inCall       -> endCall()
            isRequesting -> cancelRequest()
            else         -> requestCall()
        }
    }

    private fun requestCall() {
        if (isRequesting || inCall) {
            if (!isFinishing) toast(if (inCall) "이미 통화 중입니다." else "이미 통화 요청 중입니다.")
            return
        }
        if (connectedDevice == null) {
            if (!isFinishing) toast("연결된 기기가 없습니다.")
            return
        }

        isRequesting = true
        btnCall.text = "요청 중…"
        updateStatusText("통화 요청 중...")

        try {
            gattClient.sendAudioData(byteArrayOf(AudioManager.TYPE_CALL_REQUEST))
            clearCallRequestTimeout()
            callRequestTimeoutRunnable = Runnable {
                if (isRequesting) {
                    cancelRequest()
                    if (!isFinishing) toast("요청 시간 초과")
                    updateStatusText("요청 시간 초과")
                }
            }
            handler.postDelayed(callRequestTimeoutRunnable!!, MAX_CALL_REQUEST_TIMEOUT)
        } catch (e: Exception) {
            clearCallRequestTimeout()
            isRequesting = false
            btnCall.text = "통화 시작"
            updateStatusText("통화 요청 실패")
            if (!isFinishing) toast("통화 요청 실패")
        }
    }

    private fun clearCallRequestTimeout() {
        callRequestTimeoutRunnable?.let { handler.removeCallbacks(it) }
        callRequestTimeoutRunnable = null
    }

    private fun cancelRequest() {
        clearCallRequestTimeout()
        if (isRequesting) {
            isRequesting = false
            btnCall.text = "통화 시작"
            updateStatusText("요청 취소됨")
            if (!isFinishing) toast("요청 취소")
        }
    }

    private fun acceptCall() {
        try {
            gattClient.sendAudioData(byteArrayOf(AudioManager.TYPE_CALL_ACCEPT))
            updateStatusText("통화 수락됨")
        } catch (e: Exception) {
            toast("통화 수락 실패")
        }
    }

    private fun endCall() {
        clearCallRequestTimeout()
        val wasInCall = inCall
        val wasRequesting = isRequesting

        stopDuplex() // Sets inCall = false

        try {
            gattClient.sendAudioData(byteArrayOf(AudioManager.TYPE_CALL_END))
            // UI update after successful send
            if (wasInCall || wasRequesting) { // Update UI if state actually changed
                btnCall.text = "통화 시작"
                updateStatusText("통화 종료됨")
                if (wasInCall && !isFinishing) toast("통화 종료") // Only toast if was actually in call
                else if (wasRequesting && !isFinishing) toast("요청 취소됨 (종료 신호 보냄)")
            }
        } catch (e: Exception) {
            // UI update even if send fails, but indicate local termination
            if (wasInCall || wasRequesting) {
                btnCall.text = "통화 시작"
                updateStatusText("통화 종료(로컬)")
                if (wasInCall && !isFinishing) toast("통화 종료(메시지 전송 실패)")
                else if (wasRequesting && !isFinishing) toast("요청 취소 실패(메시지 전송 실패)")
            }
        } finally {
            // Ensure isRequesting is also reset if endCall is invoked
            if (wasRequesting) {
                isRequesting = false
            }
            // Redundant UI update if already done, but ensures consistency
            if (wasInCall || wasRequesting) {
                btnCall.text = "통화 시작"
                // updateStatusText("통화 종료됨") // Already handled, or specific message based on send success
            }
        }
    }

    private fun startDuplex() {
        scanner.disablePeriodicScanning()
        advertiser.stopAdvertising()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            toast("녹음 권한 필요")
            updateStatusText("녹음 권한 없음")
            return
        }

        val frameSize = SAMPLE_RATE / 1000 * 20
        val bufSize = frameSize * 2

        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize
            ).apply { startRecording() }

            sendThread = Thread {
                val pcm  = ByteArray(bufSize)
                val opus = ByteArray(4000) // Sufficient buffer for Opus encoded data
                try {
                    while (inCall && recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val read = recorder!!.read(pcm, 0, bufSize)
                        if (read == bufSize) {
                            val ts = SystemClock.uptimeMillis().toInt()
                            val header = ByteBuffer.allocate(5)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .put(AudioManager.TYPE_AUDIO_DATA)
                                .putInt(ts)
                                .array()
                            try {
                                val encLen = opusEnc.encode(pcm, 0, frameSize, opus, 0, opus.size)
                                gattServer.broadcastAudioData(header + opus.copyOf(encLen))
                            } catch (e: Exception) {
                                // Opus encoding/send error
                            }
                            Thread.sleep(20)
                        }
                    }
                } catch (e: Exception) {
                    // Audio thread error
                }
            }.also { it.start() }
            updateStatusText("통화 중")
        } catch (e: Exception) {
            toast("오디오 레코드 초기화 실패")
            updateStatusText("오디오 초기화 실패")
        }
    }

    private fun stopDuplex() {
        inCall = false
        try {
            recorder?.run { if (recordingState == AudioRecord.RECORDSTATE_RECORDING) stop() }
            recorder?.release()
        } catch (e: Exception) { /* 레코더 정리 중 오류 */ } finally { recorder = null }

        try { sendThread?.interrupt() } catch (e: Exception) { /* 스레드 중단 중 오류 */ } finally { sendThread = null }
        try {
            streamTrack?.stop()
            streamTrack?.release()
        } catch (e: Exception) { /* 오디오 트랙 정리 중 오류 */ } finally { streamTrack = null }

        // updateStatusText("통화 종료됨") // Often called before specific endCall UI updates
        advertiser.startAdvertising()
        scanner.enablePeriodicScanning()
    }

    private fun handleRx(address: String, data: ByteArray) {
        if (data.isEmpty()) return
        when (data[0]) {
            AudioManager.TYPE_CALL_REQUEST -> handleCallRequest(address)
            AudioManager.TYPE_CALL_ACCEPT -> handleCallAccept(address)
            AudioManager.TYPE_CALL_END -> handleCallEnd(address)
            AudioManager.TYPE_AUDIO_DATA -> handleAudioData(data)
            AudioManager.TYPE_HEARTBEAT -> handleHeartbeat(address)
            AudioManager.TYPE_HEARTBEAT_ACK -> handleHeartbeatAck(address)
        }
    }

    private fun handleCallRequest(address: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("통화 요청")
                .setMessage("$address 에서 통화 요청이 왔습니다. 수락할까요?")
                .setPositiveButton("수락") { _, _ ->
                    acceptCall()
                    inCall = true
                    btnCall.text = "통화 종료"
                    startDuplex()
                    updateStatusText("통화 시작됨 (수락)")
                }
                .setNegativeButton("거절") { _, _ ->
                    toast("거절됨")
                    updateStatusText("통화 거절됨")
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun handleCallAccept(address: String) {
        if (isRequesting) {
            clearCallRequestTimeout()
            isRequesting = false
            inCall = true
            runOnUiThread {
                btnCall.text = "통화 종료"
                updateStatusText("통화 시작됨 (요청 수락)")
                if (!isFinishing) toast("통화 시작")
            }
            startDuplex()
        } else {
            // Not in requesting state, might be late/duplicate
        }
    }

    private fun handleCallEnd(address: String) {
        clearCallRequestTimeout()
        if (inCall) {
            stopDuplex() // Sets inCall = false
            runOnUiThread {
                btnCall.text = "통화 시작"
                updateStatusText("상대방이 통화를 종료함")
                if (!isFinishing) toast("상대 종료")
            }
        }
        if (isRequesting) { // If was requesting and received END (rejection)
            isRequesting = false
            runOnUiThread {
                btnCall.text = "통화 시작"
                updateStatusText("통화 요청 거절됨 (상대방)")
                if (!isFinishing) toast("요청 거절됨")
            }
        }
    }

    private fun handleAudioData(data: ByteArray) {
        try {
            // val ts = ByteBuffer.wrap(data, 1, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() // Timestamp currently not used for playback sync
            val enc = data.copyOfRange(5, data.size)
            val pcmBuf = ShortArray(1920) // Max possible PCM samples for 24kHz, 20ms stereo (though it's mono)
            try {
                val outCount = opusDec.decode(enc, 0, enc.size, pcmBuf, 0, pcmBuf.size, false)
                val pcm = ByteBuffer.allocate(outCount * 2)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .apply { for (i in 0 until outCount) putShort(pcmBuf[i]) }
                    .array()
                handler.post { playChunk(pcm) }
            } catch (e: Exception) {
                // Audio decoding error
            }
        } catch (e: Exception) {
            // Audio data processing error
        }
    }

    private fun playChunk(chunk: ByteArray) {
        if (streamTrack == null) {
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                streamTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuf, chunk.size * 4))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                    .apply { play() }
            } catch (e: Exception) {
                return // AudioTrack creation failed
            }
        }
        try {
            streamTrack?.write(chunk, 0, chunk.size)
        } catch (e: Exception) {
            // Audio playback error
        }
    }

    private fun updateStatusText(status: String) {
        runOnUiThread {
            val currentText = tvStatus.text.toString()
            if (!currentText.startsWith(status)) { // Avoids flickering if prefix is same
                tvStatus.text = status
            }
        }
    }

    private fun toast(msg: String) =
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    override fun onDestroy() {
        super.onDestroy()
        stopDuplex()
        advertiser.stopAdvertising()
        scanner.disablePeriodicScanning() // Changed from stopScanning to disablePeriodicScanning
        gattServer.closeServer()
        gattClient.disconnectAll()
        handler.removeCallbacksAndMessages(null)
    }
}