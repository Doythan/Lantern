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
    
    // 초기 메시지 설정
    fun initializeDefaultMessages() {
        if (_messages.value.isEmpty()) {
            val initialMessages = listOf(
                ChatMessage(1, "시스템", "모두의 광장에 오신 것을 환영합니다. 주변 사람들과 자유롭게 대화해보세요!", System.currentTimeMillis() - 3600000, false),
                ChatMessage(2, "김싸피", "안녕하세요! 반갑습니다~ 오늘 날씨가 정말 좋네요", System.currentTimeMillis() - 1800000, false),
                ChatMessage(3, "이테마", "네 맞아요! 오늘 같은 날은 산책하기 좋을 것 같아요", System.currentTimeMillis() - 1500000, false),
                ChatMessage(4, "박비트", "저는 지금 카페에서 코딩하고 있어요 ☕", System.currentTimeMillis() - 1200000, false),
                ChatMessage(5, "최앱", "와~ 저도 노트북 들고 카페 가려고 했는데! 어느 카페인가요?", System.currentTimeMillis() - 900000, false),
                ChatMessage(6, "정리액트", "여기 사람들 다 개발자인가요?", System.currentTimeMillis() - 600000, false),
                ChatMessage(7, "한고민", "저는 디자이너입니다~ 혹시 협업할 개발자 구하시나요?", System.currentTimeMillis() - 300000, false)
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