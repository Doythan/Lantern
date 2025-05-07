package com.example.be.login.service;

import com.example.be.login.dto.GoogleRequestDto;
import com.example.be.login.dto.GoogleResponseDto;
import com.example.be.login.entity.User;
import com.example.be.login.mapper.UserMapper;
import com.example.be.login.util.JwtUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;  // 변경
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class GoogleServiceImpl implements GoogleService {
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    @Value("${google.client-id}")
    private String googleClientId;

    @Override
    public GoogleResponseDto authenticate(GoogleRequestDto request) {
        try {
            // 1. GoogleIdTokenVerifier 생성
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance()          // JacksonFactory → GsonFactory
            )
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            // 2. ID 토큰 검증
            GoogleIdToken idToken = verifier.verify(request.getIdToken());
            if (idToken == null) {
                throw new IllegalArgumentException("Invalid Google ID token.");
            }

            // 3. Payload 에서 사용자 정보 추출
            GoogleIdToken.Payload payload = idToken.getPayload();
            String email    = payload.getEmail();
            // 이메일이 항상 존재하니 split은 getEmail()로 처리
            String nickName = payload.get("name") != null
                    ? payload.get("name").toString()
                    : email.split("@")[0];

            // 4. DB 조회·가입 처리
            User user = userMapper.selectByEmail(email);
            if (user == null) {
                user = new User(null, email, nickName);
                userMapper.insertUser(user);
            }

            // 5. JWT 발급
            String token = jwtUtil.generateToken(email);


            // 6. 응답 DTO 반환
            return new GoogleResponseDto(token, email, nickName, user.getUserId());

        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Failed to verify Google ID token", e);
        }
    }
}
