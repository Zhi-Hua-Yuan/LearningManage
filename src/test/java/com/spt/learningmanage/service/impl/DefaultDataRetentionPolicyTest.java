package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.config.DataCleanupProperties;
import com.spt.learningmanage.constant.CleanupResourceTypeEnum;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultDataRetentionPolicyTest {
    @Test
    void appliesFrozenStage7RetentionWindows() {
        DataCleanupProperties properties = new DataCleanupProperties();
        DefaultDataRetentionPolicy policy = new DefaultDataRetentionPolicy(properties);
        LocalDateTime now = LocalDateTime.of(2026, 9, 7, 2, 30);

        assertEquals(now.minusDays(30), policy.cutoff(CleanupResourceTypeEnum.AI_CALL_BODY, now));
        assertEquals(now.minusDays(90), policy.cutoff(CleanupResourceTypeEnum.AGENT_HISTORY, now));
        assertEquals(now.minusDays(14), policy.cutoff(CleanupResourceTypeEnum.KNOWLEDGE_EVENT, now));
        assertEquals(now.minusDays(180), policy.cutoff(CleanupResourceTypeEnum.ADMIN_AUDIT, now));
        assertEquals("stage7-v1", policy.version());
    }
}
