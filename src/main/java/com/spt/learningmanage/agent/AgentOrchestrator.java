package com.spt.learningmanage.agent;

import com.spt.learningmanage.model.entity.AiAgentRun;

public interface AgentOrchestrator {
    AgentOrchestrationResult orchestrate(AiAgentRun run);
}

