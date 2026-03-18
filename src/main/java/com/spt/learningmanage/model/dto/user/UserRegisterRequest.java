package com.spt.learningmanage.model.dto.user;

import lombok.Data;

@Data
public class UserRegisterRequest {
    private String account;
    private String username;
    private String password;
    private String confirmPassword;
}

