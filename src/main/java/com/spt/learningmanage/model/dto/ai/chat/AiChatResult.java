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
        AiFailureTypeEnum fallbackReason,
        List<AiAttemptSummary> attempts
) {

    public AiChatResult {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
    }

    public AiChatResult(String content,
                        List<AiToolCall> toolCalls,
                        String finishReason,
                        AiUsage usage,
                        String providerRequestId,
                        String requestedModel,
                        String actualModel,
                        Integer retryCount,
                        boolean fallbackUsed,
                        AiFailureTypeEnum fallbackReason) {
        this(content, toolCalls, finishReason, usage, providerRequestId, requestedModel,
                actualModel, retryCount, fallbackUsed, fallbackReason, List.of());
    }

    public AiChatResult withAttempts(List<AiAttemptSummary> attemptSummaries) {
        return new AiChatResult(content, toolCalls, finishReason, aggregateUsage(attemptSummaries),
                providerRequestId, requestedModel, actualModel, retryCount, fallbackUsed,
                fallbackReason, attemptSummaries);
    }

    private AiUsage aggregateUsage(List<AiAttemptSummary> attemptSummaries) {
        List<AiUsage> known = attemptSummaries.stream()
                .map(AiAttemptSummary::usage)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (known.isEmpty()) {
            return usage;
        }
        return new AiUsage(
                sumKnown(known, AiUsage::promptTokens),
                sumKnown(known, AiUsage::completionTokens),
                sumKnown(known, AiUsage::totalTokens)
        );
    }

    private Integer sumKnown(List<AiUsage> usages,
                             java.util.function.Function<AiUsage, Integer> extractor) {
        List<Integer> values = usages.stream().map(extractor).filter(java.util.Objects::nonNull).toList();
        return values.isEmpty() ? null : values.stream().mapToInt(value -> Math.max(value, 0)).sum();
    }
}
