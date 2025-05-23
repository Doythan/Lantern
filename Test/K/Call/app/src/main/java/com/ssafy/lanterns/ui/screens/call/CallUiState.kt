package com.ssafy.lanterns.ui.screens.call

/**
 * 통화 UI 상태 정의
 */
sealed class CallUiState {
    /**
     * 통화 대기 상태
     */
    object Idle : CallUiState()
    
    /**
     * 발신 통화 상태
     */
    data class OutgoingCall(
        val deviceAddress: String
    ) : CallUiState()
    
    /**
     * 수신 통화 상태
     */
    data class IncomingCall(
        val deviceAddress: String,
        val deviceName: String
    ) : CallUiState()
    
    /**
     * 통화중 상태
     */
    data class OngoingCall(
        val deviceAddress: String,
        val deviceName: String,
        val isMyTurn: Boolean = false,
        val opponentIsSpeaking: Boolean = false,
        val waitingForTurn: Boolean = false,
        val callDurationSeconds: Int = 0
    ) : CallUiState()
    
    /**
     * 오류 상태
     */
    data class Error(
        val message: String
    ) : CallUiState()
} 