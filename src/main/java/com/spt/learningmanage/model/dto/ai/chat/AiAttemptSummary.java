package com.spt.learningmanage.model.dto.ai.chat;

import com.spt.learningmanage.constant.AiFailureTypeEnum;

public record AiAttemptSummary(
        String model,
        AiUsage usage,
        String providerRequestId,
        AiFailureTypeEnum failureType,
        long durationMs
) {

    public AiAttemptSummary {
        durationMs = Math.max(durationMs, 0L);
    }
}
