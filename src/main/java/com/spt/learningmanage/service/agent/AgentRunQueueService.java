package com.spt.learningmanage.service.agent;

import com.spt.learningmanage.model.entity.AiAgentRun;

import java.util.List;

public interface AgentRunQueueService {
    List<AiAgentRun> claimReady(String workerId, int limit);

    boolean heartbeat(AiAgentRun run);

    boolean updateProgress(AiAgentRun run, String step, int toolCount, long dataVersion);

    boolean cancellationRequested(String runId, String executionToken);

    boolean complete(AiAgentRun run, AgentRunCompletion completion);
}

