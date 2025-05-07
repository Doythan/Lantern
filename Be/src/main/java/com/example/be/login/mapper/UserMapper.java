package com.example.be.login.mapper;

import com.example.be.login.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {
    User selectByEmail(String email);
    void insertUser(User user);
}
