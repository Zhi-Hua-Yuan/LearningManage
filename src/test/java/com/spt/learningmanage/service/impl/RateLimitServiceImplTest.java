package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.config.RateLimitProperties;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;

class RateLimitServiceImplTest {

    private StringRedisTemplate redisTemplate;
    private RateLimitProperties properties;
    private RateLimitServiceImpl service;

    @BeforeEach
    void setUp() {
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        properties = new RateLimitProperties();
        service = new RateLimitServiceImpl();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redisTemplate);
        ReflectionTestUtils.setField(service, "rateLimitProperties", properties);
    }

    @Test
    void shouldFailClosedWhenRedisIsUnavailable() {
        properties.setFailOpen(false);
        Mockito.when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("redis down"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.checkAiRateLimit(1L, "task-breakdown"));

        assertEquals(ErrorCode.OPERATION_ERROR, exception.getErrorCode());
    }

    @Test
    void shouldFailOpenWhenConfiguredForDevelopment() {
        properties.setFailOpen(true);
        Mockito.when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("redis down"));

        assertDoesNotThrow(() -> service.checkAiRateLimit(1L, "task-breakdown"));
    }

    @Test
    void shouldRejectRequestBeyondSceneLimit() {
        properties.getDefaultRule().setMaxRequests(1);
        Mockito.when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(2L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.checkAiRateLimit(1L, "unknown-scene"));

        assertEquals(ErrorCode.RATE_LIMIT_ERROR, exception.getErrorCode());
    }
}
