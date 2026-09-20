package com.spt.learningmanage.model.dto.ai.chat;

import java.util.List;

/**
 * Internal, transport-neutral streaming event. It is deliberately not a REST
 * DTO; Phase 3 owns the eventual SSE envelope and final citation checks.
 */
public record AiStreamingChunk(
        String contentDelta,
        List<AiToolCall> toolCalls,
        String finishReason,
        AiUsage usage,
        String actualModel,
        String providerRequestId,
        boolean terminal
) {

    public AiStreamingChunk {
        contentDelta = contentDelta == null ? "" : contentDelta;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
