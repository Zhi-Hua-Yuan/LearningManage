package com.spt.learningmanage.model.dto.ai.chat;

import com.spt.learningmanage.constant.AiFailureTypeEnum;

import java.util.List;

public record AiChatResult(
        String content,
        List<AiToolCall> toolCalls,
        String finishReason,
        AiUsage usage,
        String providerRequestId,
        String requestedModel,
        String actualModel,
        Integer retryCount,
        boolean fallbackUsed,
        AiFailureTypeEnum fallbackReason
) {

    public AiChatResult {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
