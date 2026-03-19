package com.spt.learningmanage.service;

import com.spt.learningmanage.model.entity.User;
import com.spt.learningmanage.model.vo.UserLoginVo;

public interface UserService {
    Long register(String account, String username, String password, String confirmPassword);

    UserLoginVo login(String account, String password);
}

