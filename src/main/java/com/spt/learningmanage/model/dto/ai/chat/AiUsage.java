package com.spt.learningmanage.model.dto.ai.chat;

public record AiUsage(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
) {
}
