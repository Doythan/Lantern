package com.example.be.login.service;

import com.example.be.login.dto.GoogleRequestDto;
import com.example.be.login.dto.GoogleResponseDto;

public interface GoogleService {
    GoogleResponseDto authenticate(GoogleRequestDto request);
}
