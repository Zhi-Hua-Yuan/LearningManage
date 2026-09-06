package com.spt.learningmanage.service;

import com.spt.learningmanage.model.dto.agent.AgentProjectRiskRequest;
import com.spt.learningmanage.model.dto.agent.AgentTeamWorkloadRequest;
import com.spt.learningmanage.model.vo.agent.AgentCancelVO;
import com.spt.learningmanage.model.vo.agent.AgentRunCreatedVO;
import com.spt.learningmanage.model.vo.agent.AgentRunVO;

public interface AgentRunService {
    AgentRunCreatedVO submitProjectRisk(AgentProjectRiskRequest request);

    AgentRunCreatedVO submitTeamWorkload(AgentTeamWorkloadRequest request);

    AgentRunVO getRun(String runId);

    AgentCancelVO cancel(String runId);
}

