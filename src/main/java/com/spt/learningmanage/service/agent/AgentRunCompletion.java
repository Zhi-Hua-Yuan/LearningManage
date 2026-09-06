package com.spt.learningmanage.service.agent;

public record AgentRunCompletion(
        String terminalStatus,
        String step,
        Long endDataVersion,
        String draftId,
        Long aiCallLogId,
        String partialReason,
        String failureType,
        String errorSummary,
        String model,
        String promptCode,
        Integer promptVersion
) {
}
