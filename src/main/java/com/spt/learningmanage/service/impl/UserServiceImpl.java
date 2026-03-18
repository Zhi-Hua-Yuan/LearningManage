package com.spt.learningmanage.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.UserMapper;
import com.spt.learningmanage.model.entity.User;
import com.spt.learningmanage.model.vo.UserLoginVo;
import com.spt.learningmanage.service.UserService;
import com.spt.learningmanage.utils.JwtUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Override
    public Long register(String account, String username, String password, String confirmPassword) {
        // 参数校验
        if (StrUtil.isBlank(account) || account.length() < 4 || account.length() > 20 || account.contains(" ")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号格式不合法");
        }
        if (StrUtil.isBlank(password) || password.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度不能少于8位");
        }
        if (!password.equals(confirmPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        if (StrUtil.isBlank(username) || username.length() < 2 || username.length() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名格式不合法");
        }
        // 查重
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("account", account);
        Long count = userMapper.selectCount(queryWrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号已存在");
        }
        // 密码加密
        String encryptedPwd = BCrypt.hashpw(password, BCrypt.gensalt());
        // 保存用户
        User user = new User();
        user.setAccount(account);
        user.setUsername(username);
        user.setPassword(encryptedPwd);
        user.setUserRole("user");
        int result = userMapper.insert(user);
        if (result <= 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "注册失败");
        }
        return user.getId();
    }

    @Override
    public UserLoginVo login(String account, String password) {
        // 查询用户
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("account", account);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码错误");
        }
        // 验证密码
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码错误");
        }
        // 签发 token
        String token = JwtUtils.createToken(user.getId());
        // 返回登录视图对象
        UserLoginVo vo = new UserLoginVo();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setToken(token);
        return vo;
    }
}
