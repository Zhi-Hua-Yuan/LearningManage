package com.spt.learningmanage.controller;

import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.model.dto.user.UserLoginRequest;
import com.spt.learningmanage.model.dto.user.UserRegisterRequest;
import com.spt.learningmanage.model.dto.user.UserUpdatePasswordRequest;
import com.spt.learningmanage.model.dto.user.UserUpdateRequest;
import com.spt.learningmanage.model.vo.user.UserLoginVo;
import com.spt.learningmanage.model.vo.user.UserVO;
import com.spt.learningmanage.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    // 1. 注册接口 (已修复)
    @PostMapping("/register")
    public BaseResponse<Long> register(@RequestBody UserRegisterRequest request) {
        Long userId = userService.register(
                request.getAccount(),
                request.getUsername(),
                request.getPassword(),
                request.getConfirmPassword()
        );
        return ResultUtils.ok(userId);
    }

    // 2. 登录接口
    @PostMapping("/login")
    public BaseResponse<UserLoginVo> login(@RequestBody UserLoginRequest request) {
        UserLoginVo loginUserVo = userService.login(request.getAccount(), request.getPassword());
        return ResultUtils.ok(loginUserVo);
    }

    // 3. 退出登录
    @PostMapping("/logout")
    public BaseResponse<Boolean> logout() {
        // 目前前端直接清除本地 Token 即可，后端直接返回 true。后续接入 Redis 可在此将 Token 加入黑名单
        return ResultUtils.ok(true);
    }

    // 4. 获取当前登录用户信息
    @GetMapping("/me")
    public BaseResponse<UserVO> getLoginUser() {
        UserVO userVO = userService.getLoginUser();
        return ResultUtils.ok(userVO);
    }

    // 5. 修改个人信息
    @PostMapping("/update")
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest request) {
        userService.updateUser(request);
        return ResultUtils.ok(true);
    }

    // 6. 修改密码
    @PostMapping("/password/update")
    public BaseResponse<Boolean> updatePassword(@RequestBody UserUpdatePasswordRequest request) {
        userService.updatePassword(request.getOldPassword(), request.getNewPassword());
        return ResultUtils.ok(true);
    }
}