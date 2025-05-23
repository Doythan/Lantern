package com.ssafy.lanterns.service.call.manager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ssafy.lanterns.R
import com.ssafy.lanterns.service.call.CallService
import com.ssafy.lanterns.ui.view.main.MainActivity
import java.util.concurrent.TimeUnit

/**
 * 통화 알림 관리자
 * 통화 상태에 따른 알림을 생성하고 관리합니다.
 */
class CallNotificationManager(private val context: Context) {
    private val TAG = "LANT_CallNotificationManager"
    
    // 알림 관련 상수
    companion object {
        const val NOTIFICATION_ID = 100
        const val NOTIFICATION_CHANNEL_ID = "call_service_channel"
    }
    
    // 현재 통화 상태
    private var currentCallState = CallService.CallState.IDLE
    
    /**
     * 알림 채널 생성
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "통화 서비스"
            val descriptionText = "통화 상태를 표시합니다"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(true)
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            
            Log.i(TAG, "알림 채널 생성됨: $NOTIFICATION_CHANNEL_ID")
        }
    }
    
    /**
     * 통화 상태 변경 처리
     */
    fun onCallStateChanged(newState: CallService.CallState) {
        currentCallState = newState
    }
    
    /**
     * 통화 알림 생성
     * 
     * @param callState 현재 통화 상태
     * @param deviceName 통화 상대방 이름
     * @param callDuration 통화 지속 시간 (밀리초 단위)
     * @return 생성된 알림 객체
     */
    fun createCallNotification(
        callState: CallService.CallState,
        deviceName: String?,
        callDuration: Long = 0
    ): Notification {
        // 메인 액티비티로 돌아가는 인텐트
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context, 0, mainIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 기본 알림 빌더
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call)
            .setContentIntent(mainPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
        
        // 현재 통화 상태에 따라 알림 내용 조정
        when (callState) {
            CallService.CallState.OUTGOING -> {
                // 통화 종료 인텐트
                val endCallIntent = Intent(context, CallService::class.java).apply {
                    action = CallService.ACTION_END_CALL
                }
                val endCallPendingIntent = PendingIntent.getService(
                    context, 1, endCallIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                builder.setContentTitle("발신 중")
                    .setContentText("${deviceName ?: "알 수 없음"}에게 연결 중...")
                    .addAction(R.drawable.ic_call_end, "종료", endCallPendingIntent)
            }
            
            CallService.CallState.INCOMING -> {
                // 통화 수락 인텐트
                val acceptCallIntent = Intent(context, CallService::class.java).apply {
                    action = CallService.ACTION_ACCEPT_CALL
                }
                val acceptCallPendingIntent = PendingIntent.getService(
                    context, 2, acceptCallIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                // 통화 거절 인텐트
                val rejectCallIntent = Intent(context, CallService::class.java).apply {
                    action = CallService.ACTION_REJECT_CALL
                }
                val rejectCallPendingIntent = PendingIntent.getService(
                    context, 3, rejectCallIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                builder.setContentTitle("수신 전화")
                    .setContentText("${deviceName ?: "알 수 없음"}에서 전화가 왔습니다")
                    .addAction(R.drawable.ic_call_accept, "수락", acceptCallPendingIntent)
                    .addAction(R.drawable.ic_call_end, "거절", rejectCallPendingIntent)
            }
            
            CallService.CallState.CONNECTED -> {
                // 통화 종료 인텐트
                val endCallIntent = Intent(context, CallService::class.java).apply {
                    action = CallService.ACTION_END_CALL
                }
                val endCallPendingIntent = PendingIntent.getService(
                    context, 1, endCallIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                // 통화 시간 표시
                val durationText = getCallDurationText(callDuration)
                
                builder.setContentTitle("통화 중")
                    .setContentText("${deviceName ?: "알 수 없음"}와 통화 중... ($durationText)")
                    .addAction(R.drawable.ic_call_end, "종료", endCallPendingIntent)
            }
            
            CallService.CallState.ERROR -> {
                builder.setContentTitle("통화 오류")
                    .setContentText("통화 중 오류가 발생했습니다")
            }
            
            CallService.CallState.IDLE -> {
                builder.setContentTitle("통화 대기")
                    .setContentText("통화 서비스가 실행 중입니다")
            }
        }
        
        return builder.build()
    }
    
    /**
     * 알림 업데이트
     */
    fun updateCallNotification(
        callState: CallService.CallState,
        deviceName: String?,
        callDuration: Long = 0
    ) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createCallNotification(callState, deviceName, callDuration))
    }
    
    /**
     * 통화 지속 시간 텍스트 반환
     */
    private fun getCallDurationText(callDuration: Long): String {
        if (callDuration == 0L) return "00:00"
        
        // 밀리초를 초로 변환
        val durationSeconds = callDuration / 1000
        
        val hours = TimeUnit.SECONDS.toHours(durationSeconds)
        val minutes = TimeUnit.SECONDS.toMinutes(durationSeconds) - 
                TimeUnit.HOURS.toMinutes(hours)
        val seconds = TimeUnit.SECONDS.toSeconds(durationSeconds) - 
                TimeUnit.MINUTES.toSeconds(minutes) - 
                TimeUnit.HOURS.toSeconds(hours)
        
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
} 