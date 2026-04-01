package com.spt.learningmanage.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.UserMapper;
import com.spt.learningmanage.model.dto.user.UserUpdateRequest;
import com.spt.learningmanage.model.entity.User;
import com.spt.learningmanage.model.vo.user.UserLoginVo;
import com.spt.learningmanage.model.vo.user.UserVO;
import com.spt.learningmanage.service.UserService;
import com.spt.learningmanage.utils.JwtUtils;
import com.spt.learningmanage.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Override
    public Long register(String userAccount, String username, String userPassword, String checkPassword) {
        // 参数校验
        if (StrUtil.isBlank(userAccount)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号不能为空，请输入 4-20 位字符且不能包含空格");
        }
        if (userAccount.length() < 4 || userAccount.length() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "账号长度需为 4-20 位，当前长度为 " + userAccount.length() + " 位");
        }
        if (userAccount.contains(" ")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号不能包含空格，请去掉空格后重试");
        }

        if (StrUtil.isBlank(userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码不能为空，请输入至少 8 位密码");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "密码长度不能少于 8 位，当前长度为 " + userPassword.length() + " 位");
        }

        if (StrUtil.isBlank(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请再次输入确认密码");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致，请检查后重试");
        }
        // 查重
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("account", userAccount);
        Long count = userMapper.selectCount(queryWrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该账号已被注册，请更换账号或直接登录");
        }
        // 密码加密
        String encryptedPwd = BCrypt.hashpw(userPassword, BCrypt.gensalt());
        // 保存用户
        User user = new User();
        user.setAccount(userAccount);
        user.setUsername(StrUtil.isNotBlank(username) ? username.trim() : UUID.randomUUID().toString().substring(0, 10));
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
        if (StrUtil.isBlank(account)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号不能为空，请输入注册时的账号");
        }
        if (StrUtil.isBlank(password)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码不能为空，请输入登录密码");
        }

        // 查询用户
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("account", account);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码错误，请检查后重试");
        }
        // 验证密码
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码错误，请检查后重试");
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

    @Override
    public UserVO getLoginUser() {
        User user = getCurrentUser();
        UserVO userVO = new UserVO();
        userVO.setId(user.getId());
        userVO.setAccount(user.getAccount());
        userVO.setUsername(user.getUsername());
        userVO.setUserRole(user.getUserRole());
        userVO.setCreateTime(user.getCreateTime());
        return userVO;
    }

    @Override
    public void updateUser(UserUpdateRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "修改资料失败：请求体不能为空，请至少传入 username");
        }
        String username = StrUtil.trim(request.getUsername());
        if (StrUtil.isBlank(username)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名不能为空，请输入 2-20 个字符");
        }
        if (username.length() < 2 || username.length() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "用户名长度需为 2-20 位，当前长度为 " + username.length() + " 位");
        }

        User currentUser = getCurrentUser();
        User updateUser = new User();
        updateUser.setId(currentUser.getId());
        updateUser.setUsername(username);
        int rows = userMapper.updateById(updateUser);
        if (rows <= 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新用户信息失败，请稍后重试");
        }
    }

    @Override
    public void updatePassword(String oldPassword, String newPassword) {
        if (StrUtil.isBlank(oldPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "旧密码不能为空，请输入当前登录密码");
        }
        if (StrUtil.isBlank(newPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "新密码不能为空，请输入至少 8 位的新密码");
        }
        if (newPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "新密码长度不能少于 8 位，当前长度为 " + newPassword.length() + " 位");
        }

        User currentUser = getCurrentUser();
        if (!BCrypt.checkpw(oldPassword, currentUser.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR, "旧密码校验失败：你输入的旧密码与当前账号不匹配");
        }
        if (BCrypt.checkpw(newPassword, currentUser.getPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "新密码不能与旧密码相同，请更换一个新密码");
        }

        User updateUser = new User();
        updateUser.setId(currentUser.getId());
        updateUser.setPassword(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        int rows = userMapper.updateById(updateUser);
        if (rows <= 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新密码失败，请稍后重试");
        }
    }

    private User getCurrentUser() {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "当前请求未携带有效登录信息，请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "当前登录用户不存在或已被删除，请重新登录");
        }
        return user;
    }
}
