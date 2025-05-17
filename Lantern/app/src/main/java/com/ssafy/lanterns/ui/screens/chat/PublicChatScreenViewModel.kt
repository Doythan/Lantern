package com.ssafy.lanterns.ui.screens.chat

import androidx.lifecycle.ViewModel
import com.ssafy.lanterns.data.model.User
import com.ssafy.lanterns.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

@HiltViewModel
class PublicChatScreenViewModel @Inject constructor(
    private val userRepository: UserRepository
): ViewModel() {
    private val _currentUser = mutableStateOf<User?>(null)
    val currentUser: State<User?> = _currentUser // 외부에서 읽기 전용
    
    // 메시지 상태 관리
    private val _messages = mutableStateOf<List<ChatMessage>>(emptyList())
    val messages: State<List<ChatMessage>> = _messages
    
    init {
        viewModelScope.launch {
            _currentUser.value = userRepository.getUser()
        }
    }
    
    // 초기 메시지 설정 (시스템 메시지만 남기고 더미 데이터 제거)
    fun initializeDefaultMessages() {
        if (_messages.value.isEmpty()) {
            val initialMessages = listOf(
                ChatMessage(
                    id = 1, 
                    sender = "시스템", 
                    text = "모두의 광장에 오신 것을 환영합니다. 주변 사람들과 자유롭게 대화해보세요!", 
                    time = System.currentTimeMillis() - 3600000, 
                    isMe = false,
                    senderProfileId = -1 // -1은 확성기 아이콘(R.drawable.public_1)을 사용함을 나타냄
                )
            )
            _messages.value = initialMessages
        }
    }
    
    // 메시지 추가 함수
    fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }
    
    // 다음 메시지 ID 가져오기
    fun getNextMessageId(): Int {
        return _messages.value.size + 1
    }
}