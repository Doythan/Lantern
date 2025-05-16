package com.ssafy.lanterns.ui.screens.chat

import androidx.lifecycle.ViewModel
import com.ssafy.lanterns.data.model.User
import com.ssafy.lanterns.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

@HiltViewModel
class PublicChatScreenViewModel @Inject constructor(
    private val userRepository: UserRepository
): ViewModel(){
    private val _currentUser = mutableStateOf<User?>(null)
    val currentUser get() = _currentUser // 외부에서 읽기 전용

    init{
        viewModelScope.launch {
            _currentUser.value = userRepository.getUser()
        }
    }
}