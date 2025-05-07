package com.example.be.login.service;

import com.example.be.login.entity.User;
import com.example.be.login.mapper.UserMapper;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final UserMapper userMapper;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauthUser = delegate.loadUser(userRequest);

        String email = oauthUser.getAttribute("email");
        String name  = oauthUser.getAttribute("name");

        // DB 조회·가입 로직
        User user = userMapper.selectByEmail(email);
        if (user == null) {
            user = new User(null, email, name);
            userMapper.insertUser(user);
        }
        // 이후 JWT 발급 또는 추가 처리 가능

        return oauthUser;
    }
}
