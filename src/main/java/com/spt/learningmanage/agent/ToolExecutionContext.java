package com.spt.learningmanage.agent;

import com.spt.learningmanage.constant.AgentSceneEnum;
import com.spt.learningmanage.model.permission.ProjectAccessScope;

public record ToolExecutionContext(
        Long actorUserId,
        String runId,
        AgentSceneEnum scene,
        Long projectId,
        Long teamId,
        ProjectAccessScope projectAccessScope,
        String traceId,
        int attemptNo,
        String executionToken,
        long dataVersion
) {
}
