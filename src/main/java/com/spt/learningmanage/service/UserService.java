package com.spt.learningmanage.service;

import com.spt.learningmanage.model.dto.user.UserUpdateRequest;
import com.spt.learningmanage.model.vo.user.UserLoginVo;
import com.spt.learningmanage.model.vo.user.UserVO;

public interface UserService {

    Long register(String userAccount, String username, String userPassword, String checkPassword);

    UserLoginVo login(String account, String password);

    UserVO getLoginUser();

    void updateUser(UserUpdateRequest request);

    void updatePassword(String oldPassword, String newPassword);
}
