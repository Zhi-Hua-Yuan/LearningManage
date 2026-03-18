package com.spt.learningmanage.controller;

import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.model.dto.user.UserLoginRequest;
import com.spt.learningmanage.model.dto.user.UserRegisterRequest;
import com.spt.learningmanage.model.entity.User;
import com.spt.learningmanage.model.vo.UserLoginVo;
import com.spt.learningmanage.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    // 注册接口
    @PostMapping("/register")
    public BaseResponse<Long> register(@RequestBody UserRegisterRequest request) {
        Long userId = userService.register(request.getAccount(), request.getUsername(), request.getPassword(), request.getConfirmPassword());
        return ResultUtils.ok(userId);
    }

    // 登录接口
    @PostMapping("/login")
    public BaseResponse<UserLoginVo> login(@RequestBody UserLoginRequest request) {
        UserLoginVo loginUserVo = userService.login(request.getAccount(), request.getPassword());
        return ResultUtils.ok(loginUserVo);
    }
}
