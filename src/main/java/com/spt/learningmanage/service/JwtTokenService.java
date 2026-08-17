package com.spt.learningmanage.service;

import cn.hutool.jwt.JWT;
import com.spt.learningmanage.config.JwtProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtTokenService {

    private final JwtProperties jwtProperties;

    public JwtTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String createToken(Long userId) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + jwtProperties.getExpireSeconds() * 1000L);
        return JWT.create()
                .setPayload("userId", userId)
                .setPayload("exp", expire.getTime() / 1000)
                .setKey(secretBytes())
                .sign();
    }

    public Long parseToken(String token) {
        try {
            if (token == null || token.isBlank()) {
                return null;
            }
            JWT jwt = JWT.of(token);
            boolean verified = jwt.setKey(secretBytes()).verify();
            Object expObj = jwt.getPayload("exp");
            Object userIdObj = jwt.getPayload("userId");
            if (!verified || expObj == null || userIdObj == null) {
                return null;
            }
            long exp = Long.parseLong(expObj.toString());
            if (System.currentTimeMillis() / 1000 >= exp) {
                return null;
            }
            return Long.valueOf(userIdObj.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] secretBytes() {
        return jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
    }
}
