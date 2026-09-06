package com.spt.learningmanage.agent;

public record AgentOrchestrationResult(
        String payloadJson,
        long dataVersion,
        int toolCount,
        boolean partial,
        String partialReason,
        Long aiCallLogId,
        String model,
        String promptCode,
        Integer promptVersion
) {
}
