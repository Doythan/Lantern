// ui/viewmodel/ChatMessage.kt
package com.example.blemodule.ui.viewmodel

data class ChatMessage(
    val senderId: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isMine: Boolean = false
)
