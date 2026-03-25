package com.spt.learningmanage.interceptor;

import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.utils.JwtUtils;
import com.spt.learningmanage.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.stereotype.Component;

/**
 * 登录校验拦截器
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取 Authorization Header
        String authHeader = request.getHeader("Authorization");

        // 2. 校验是否为空
        if (authHeader == null || authHeader.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        // --- 核心修复：处理 "Bearer " 前缀 ---
        String token = authHeader;
        if (authHeader.startsWith("Bearer ")) {
            // 如果是以 Bearer 开头，则截取第 7 位之后的内容（即真正的 Token）
            token = authHeader.substring(7);
        }
        // ----------------------------------

        // 3. 解析 Token
        try {
            Long userId = JwtUtils.parseToken(token);
            // 4. 存储用户信息到 ThreadLocal
            UserHolder.set(userId);
            return true;
        } catch (Exception e) {
            // 解析失败（Token 伪造、过期、或格式依然不对）
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 必须清理线程变量，防止内存泄漏
        UserHolder.remove();
    }
}
