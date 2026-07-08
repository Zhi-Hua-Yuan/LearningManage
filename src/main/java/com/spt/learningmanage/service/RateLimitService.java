package com.spt.learningmanage.service;

public interface RateLimitService {

    void checkAiRateLimit(Long userId, String scene);
}
