package com.spt.learningmanage.model.vo.agent;

import java.time.LocalDateTime;

public record AgentRunVO(
        String runId,
        String scene,
        String status,
        String currentStep,
        int completedToolCount,
        int maxToolCount,
        String orchestrationMode,
        boolean degraded,
        String partialReason,
        String failureType,
        String draftId,
        LocalDateTime submittedAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
