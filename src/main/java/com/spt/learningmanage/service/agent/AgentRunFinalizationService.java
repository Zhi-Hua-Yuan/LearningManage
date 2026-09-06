package com.spt.learningmanage.service.agent;

import com.spt.learningmanage.agent.AgentOrchestrationResult;
import com.spt.learningmanage.model.entity.AiAgentRun;

public interface AgentRunFinalizationService {
    void completeWithDraft(AiAgentRun run, AgentOrchestrationResult result);

    void completeWithoutDraft(AiAgentRun run, String terminalStatus, String failureType, String safeMessage);
}
