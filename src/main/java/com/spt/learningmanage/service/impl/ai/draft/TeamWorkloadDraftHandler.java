package com.spt.learningmanage.service.impl.ai.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.agent.model.AgentReportDraftPayload;
import com.spt.learningmanage.constant.AgentSceneEnum;
import com.spt.learningmanage.mapper.AiAgentRunMapper;
import com.spt.learningmanage.mapper.AiAnalysisReportMapper;
import com.spt.learningmanage.mapper.AiAnalysisReportSourceMapper;
import com.spt.learningmanage.service.BusinessDataVersionService;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.knowledge.KnowledgeHashing;
import org.springframework.stereotype.Component;

@Component
public class TeamWorkloadDraftHandler extends AbstractAgentReportDraftHandler {
    public TeamWorkloadDraftHandler(ObjectMapper objectMapper, AiAgentRunMapper runMapper,
                                    AiAnalysisReportMapper reportMapper, AiAnalysisReportSourceMapper sourceMapper,
                                    PermissionService permissionService, BusinessDataVersionService versionService,
                                    KnowledgeHashing hashing) {
        super(objectMapper, runMapper, reportMapper, sourceMapper, permissionService, versionService, hashing);
    }

    @Override public String scene() { return AgentSceneEnum.TEAM_WORKLOAD.getDraftScene(); }
    @Override protected String expectedReportType() { return "TEAM_WORKLOAD"; }
    @Override protected void authorize(Long actorUserId, AgentReportDraftPayload payload) {
        permissionService.requireTeamWorkloadAnalyze(actorUserId, payload.teamId());
    }
}
