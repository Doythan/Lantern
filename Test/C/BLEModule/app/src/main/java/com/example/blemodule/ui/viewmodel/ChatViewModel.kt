package com.example.blemodule.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    // 내부 메시지 리스트
    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    // 외부 노출용 LiveData
    val messages: LiveData<List<ChatMessage>> = _messages

    /**
     * 애플리케이션 메시지 전송 호출
     * 실제 BLE 전송 로직은 여기에 연동하세요.
     */
    fun sendAppMessage(targetId: String, payload: String) {
        // TODO: BLE 서비스에 메시지 전송 요청
        // 예시: bleService.sendMessage(targetId, payload)

        // 화면에 내 메시지 즉시 추가
        val myMsg = ChatMessage(
            senderId = "Me",
            message = payload,
            isMine = true
        )
        _messages.value = _messages.value!!.plus(myMsg)
    }

    /**
     * 외부(BLE 리스너)로부터 들어온 메시지를 추가
     */
    fun addReceivedMessage(senderId: String, payload: String) {
        viewModelScope.launch {
            val incoming = ChatMessage(
                senderId = senderId,
                message = payload,
                isMine = false
            )
            _messages.postValue(_messages.value!!.plus(incoming))
        }
    }
}
