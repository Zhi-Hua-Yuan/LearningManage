package com.spt.learningmanage.model.dto.user;

import lombok.Data;

@Data
public class UserUpdatePasswordRequest {
    private String oldPassword;
    private String newPassword;
}