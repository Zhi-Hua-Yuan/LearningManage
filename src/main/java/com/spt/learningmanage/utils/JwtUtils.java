package com.spt.learningmanage.utils;

import cn.hutool.jwt.JWT;

import java.util.Date;

public class JwtUtils {
    // 私有静态密钥
    private static final String KEY = "spt_learningmanage_jwt_secret_key";
    private static final long EXPIRE_MILLIS = 24 * 60 * 60 * 1000L; // 24小时

    /**
     * 创建Token，将userId存入Payload，过期时间24小时
     */
    public static String createToken(Long userId) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + EXPIRE_MILLIS);
        return JWT.create()
                .setPayload("userId", userId)
                .setPayload("exp", expire.getTime() / 1000)
                .setKey(KEY.getBytes())
                .sign();
    }

    /**
     * 解析Token，校验有效性和过期，返回userId，失败返回null
     */
    public static Long parseToken(String token) {
        try {
            JWT jwt = JWT.of(token);
            boolean verify = jwt.setKey(KEY.getBytes()).verify();
            Object expObj = jwt.getPayload("exp");
            if (!verify || expObj == null) {
                return null;
            }
            long exp = Long.parseLong(expObj.toString());
            long now = System.currentTimeMillis() / 1000;
            if (now > exp) {
                return null;
            }
            Object userIdObj = jwt.getPayload("userId");
            if (userIdObj == null) return null;
            return Long.valueOf(userIdObj.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
