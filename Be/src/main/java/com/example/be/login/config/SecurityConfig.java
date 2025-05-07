package com.example.be.login.config;

import com.example.be.login.service.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/login/**", "/oauth2/**").permitAll()
                        .anyRequest().authenticated()
                )

                .oauth2Login(o -> o
                        .authorizationEndpoint(a ->
                                a.baseUri("/oauth2/authorization")
                        )
                        .redirectionEndpoint(r ->
                                r.baseUri("/googlelogin/oauth2/code/*")
                        )
                        .userInfoEndpoint(u ->
                                u.userService(customOAuth2UserService)  // 빈 주입
                        )
                );
        return http.build();
    }
}
