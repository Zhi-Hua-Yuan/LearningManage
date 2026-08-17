package com.spt.learningmanage.service;

import com.spt.learningmanage.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-only-jwt-secret-with-at-least-32-bytes");
        properties.setExpireSeconds(3600);
        jwtTokenService = new JwtTokenService(properties);
    }

    @Test
    void createAndParseShouldRoundTripUserId() {
        String token = jwtTokenService.createToken(123L);

        assertEquals(123L, jwtTokenService.parseToken(token));
    }

    @Test
    void parseShouldRejectBlankOrTamperedToken() {
        String token = jwtTokenService.createToken(123L);

        assertNull(jwtTokenService.parseToken(null));
        assertNull(jwtTokenService.parseToken(""));
        assertNull(jwtTokenService.parseToken(token + "tampered"));
    }
}
