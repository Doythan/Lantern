package com.example.be.login.dto;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GoogleResponseDto {
    private String jwt;
    private String email;
    private String nickName;
    private Long userId;
}
