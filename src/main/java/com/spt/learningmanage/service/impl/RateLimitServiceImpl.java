package com.spt.learningmanage.service.impl;

import cn.hutool.core.util.StrUtil;
import com.spt.learningmanage.config.RateLimitProperties;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.service.RateLimitService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RateLimitServiceImpl implements RateLimitService {

    private static final int DEFAULT_WINDOW_SECONDS = 60;
    private static final int DEFAULT_MAX_REQUESTS = 5;
    private static final int EXPIRE_BUFFER_SECONDS = 5;
    private static final String AI_RATE_LIMIT_KEY_PREFIX = "rate_limit:ai";
    private static final String RATE_LIMIT_SCRIPT = """
            local current = redis.call('incr', KEYS[1])
            if tonumber(current) == 1 then
                redis.call('expire', KEYS[1], ARGV[1])
            end
            return current
            """;

    private final RedisScript<Long> rateLimitScript;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RateLimitProperties rateLimitProperties;

    public RateLimitServiceImpl() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(RATE_LIMIT_SCRIPT);
        script.setResultType(Long.class);
        this.rateLimitScript = script;
    }

    @Override
    public void checkAiRateLimit(Long userId, String scene) {
        if (!Boolean.TRUE.equals(rateLimitProperties.getEnabled())) {
            return;
        }
        validateRequest(userId, scene);

        RateLimitProperties.Rule rule = resolveRule(scene);
        if (!isRuleEnabled(rule)) {
            return;
        }

        int windowSeconds = normalizePositive(rule.getWindowSeconds(), DEFAULT_WINDOW_SECONDS);
        int maxRequests = normalizePositive(rule.getMaxRequests(), DEFAULT_MAX_REQUESTS);
        String normalizedScene = scene.trim();
        String key = buildAiRateLimitKey(userId, normalizedScene, windowSeconds);
        int ttlSeconds = windowSeconds + EXPIRE_BUFFER_SECONDS;

        Long count;
        try {
            count = stringRedisTemplate.execute(rateLimitScript, List.of(key), String.valueOf(ttlSeconds));
        } catch (Exception e) {
            if (isFailOpen()) {
                log.warn("AI 限流 Redis 检查失败，按配置放行。userId={}, scene={}", userId, normalizedScene, e);
                return;
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "限流服务暂不可用，请稍后再试");
        }

        if (count != null && count > maxRequests) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_ERROR, "AI 调用过于频繁，请稍后再试");
        }
    }

    private RateLimitProperties.Rule resolveRule(String scene) {
        Map<String, RateLimitProperties.Rule> sceneRules = rateLimitProperties.getSceneRules();
        RateLimitProperties.Rule sceneRule = sceneRules == null ? null : sceneRules.get(scene.trim());
        if (sceneRule != null) {
            return sceneRule;
        }
        RateLimitProperties.Rule defaultRule = rateLimitProperties.getDefaultRule();
        return defaultRule == null ? new RateLimitProperties.Rule() : defaultRule;
    }

    private boolean isRuleEnabled(RateLimitProperties.Rule rule) {
        return rule == null || rule.getEnabled() == null || rule.getEnabled();
    }

    private void validateRequest(Long userId, String scene) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (StrUtil.isBlank(scene)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "限流场景不能为空");
        }
    }

    private int normalizePositive(Integer value, int defaultValue) {
        if (value == null || value <= 0) {
            return defaultValue;
        }
        return value;
    }

    private String buildAiRateLimitKey(Long userId, String scene, int windowSeconds) {
        long windowBucket = Instant.now().getEpochSecond() / windowSeconds;
        return AI_RATE_LIMIT_KEY_PREFIX + ":" + userId + ":" + scene.trim() + ":" + windowBucket;
    }

    private boolean isFailOpen() {
        return rateLimitProperties.getFailOpen() == null || rateLimitProperties.getFailOpen();
    }
}
