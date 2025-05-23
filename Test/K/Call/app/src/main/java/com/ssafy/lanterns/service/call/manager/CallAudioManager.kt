package com.ssafy.lanterns.service.call.manager

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import com.ssafy.lanterns.config.BleConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 통화 오디오 관리 클래스
 * 마이크에서 오디오 데이터 캡처, BLE를 통한 전송, 수신된 오디오 데이터 재생을 담당합니다.
 */
class CallAudioManager(private val context: Context) {
    
    companion object {
        private const val TAG = "LANT_CallAudioManager"
        
        // 오디오 관련 상수
        private const val SAMPLE_RATE = BleConstants.AUDIO_SAMPLE_RATE // 16kHz
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2 // 버퍼 크기 조정 요소
    }
    
    // 녹음용 AudioRecord
    private var audioRecord: AudioRecord? = null
    
    // 재생용 AudioTrack
    private var audioTrack: AudioTrack? = null
    
    // 녹음 버퍼 크기
    private val recordBufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT
    ) * BUFFER_SIZE_FACTOR
    
    // 재생 버퍼 크기
    private val playbackBufferSize = AudioTrack.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_OUT_MONO,
        AUDIO_FORMAT
    ) * BUFFER_SIZE_FACTOR
    
    // 녹음 및 재생 상태 관리
    private val isRecording = AtomicBoolean(false)
    private val isPlaying = AtomicBoolean(false)
    
    // 코루틴 스코프
    private val audioScope = CoroutineScope(Dispatchers.IO + Job())
    
    // 오디오 데이터 전송 콜백
    var onAudioDataCaptured: ((ByteArray) -> Unit)? = null
    
    /**
     * 오디오 녹음 초기화
     */
    private fun initAudioRecord() {
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                recordBufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord 초기화 실패")
                audioRecord?.release()
                audioRecord = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord 초기화 중 오류: ${e.message}", e)
            audioRecord?.release()
            audioRecord = null
        }
    }
    
    /**
     * 오디오 재생 초기화
     */
    private fun initAudioTrack() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            
            val audioFormat = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AUDIO_FORMAT)
                .build()
            
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(playbackBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            
            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack 초기화 실패")
                audioTrack?.release()
                audioTrack = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack 초기화 중 오류: ${e.message}", e)
            audioTrack?.release()
            audioTrack = null
        }
    }
    
    /**
     * 오디오 녹음 시작
     */
    fun startRecording() {
        if (isRecording.get()) {
            Log.i(TAG, "이미 녹음 중입니다")
            return
        }
        
        // 오디오 녹음 초기화
        if (audioRecord == null) {
            initAudioRecord()
        }
        
        val audioRecord = this.audioRecord
        if (audioRecord == null) {
            Log.e(TAG, "AudioRecord가 초기화되지 않아 녹음을 시작할 수 없습니다")
            return
        }
        
        // 오디오 모드 설정
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        
        isRecording.set(true)
        
        audioScope.launch {
            try {
                audioRecord.startRecording()
                Log.i(TAG, "오디오 녹음 시작")
                
                val buffer = ByteArray(BleConstants.AUDIO_BUFFER_SIZE)
                
                while (isActive && isRecording.get()) {
                    val bytesRead = audioRecord.read(buffer, 0, buffer.size)
                    
                    if (bytesRead > 0) {
                        // 녹음된 오디오 데이터가 있으면 콜백을 통해 전송
                        val audioData = buffer.copyOfRange(0, bytesRead)
                        onAudioDataCaptured?.invoke(audioData)
                    }
                    
                    // 적절한 지연 추가
                    delay(10)
                }
                
                audioRecord.stop()
                Log.i(TAG, "오디오 녹음 중지")
            } catch (e: Exception) {
                Log.e(TAG, "오디오 녹음 중 오류: ${e.message}", e)
            } finally {
                isRecording.set(false)
            }
        }
    }
    
    /**
     * 오디오 녹음 중지
     */
    fun stopRecording() {
        if (!isRecording.get()) {
            return
        }
        
        isRecording.set(false)
        
        // 오디오 모드 복원
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
        
        Log.i(TAG, "오디오 녹음 중지 요청")
    }
    
    /**
     * 오디오 재생 준비
     */
    fun preparePlayback() {
        if (audioTrack == null) {
            initAudioTrack()
        }
        
        val audioTrack = this.audioTrack
        if (audioTrack == null) {
            Log.e(TAG, "AudioTrack이 초기화되지 않아 재생을 준비할 수 없습니다")
            return
        }
        
        if (audioTrack.state == AudioTrack.STATE_INITIALIZED && !isPlaying.get()) {
            audioTrack.play()
            isPlaying.set(true)
            Log.i(TAG, "오디오 재생 준비 완료")
        }
    }
    
    /**
     * 수신된 오디오 데이터 재생
     */
    fun playAudioData(audioData: ByteArray) {
        if (!isPlaying.get()) {
            preparePlayback()
        }
        
        val audioTrack = this.audioTrack
        if (audioTrack == null || !isPlaying.get()) {
            Log.e(TAG, "AudioTrack이 준비되지 않아 오디오 데이터를 재생할 수 없습니다")
            return
        }
        
        try {
            audioTrack.write(audioData, 0, audioData.size)
        } catch (e: Exception) {
            Log.e(TAG, "오디오 데이터 재생 중 오류: ${e.message}", e)
        }
    }
    
    /**
     * 오디오 재생 중지
     */
    fun stopPlayback() {
        if (!isPlaying.get()) {
            return
        }
        
        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.stop()
        
        isPlaying.set(false)
        Log.i(TAG, "오디오 재생 중지")
    }
    
    /**
     * 리소스 해제
     */
    fun release() {
        stopRecording()
        stopPlayback()
        
        audioRecord?.release()
        audioRecord = null
        
        audioTrack?.release()
        audioTrack = null
        
        audioScope.cancel()
        
        Log.i(TAG, "오디오 리소스 해제 완료")
    }
    
    /**
     * 내 턴인지에 따라 녹음/재생 상태 업데이트
     */
    fun updateAudioState(isMyTurn: Boolean) {
        if (isMyTurn) {
            // 내 턴이면 녹음 시작, 재생 중지
            stopPlayback()
            startRecording()
        } else {
            // 상대방 턴이면 녹음 중지, 재생 준비
            stopRecording()
            preparePlayback()
        }
    }
} 