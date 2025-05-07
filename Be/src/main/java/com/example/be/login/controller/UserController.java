package com.example.be.login.controller;


import com.example.be.login.dto.GoogleRequestDto;
import com.example.be.login.dto.GoogleResponseDto;
import com.example.be.login.service.GoogleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {
    private final GoogleService googleService;

    @PostMapping("/login/google")
    public ResponseEntity<GoogleResponseDto> googleLogin(@RequestBody GoogleRequestDto req) {
        GoogleResponseDto resp = googleService.authenticate(req);
        return ResponseEntity.ok(resp);
    }
}
