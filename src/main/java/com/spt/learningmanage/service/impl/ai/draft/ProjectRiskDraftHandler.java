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
import com.spt.learningmanage.service.rag.RagSourceVerifier;
import com.spt.learningmanage.constant.RagSourceValidationStatus;
import com.spt.learningmanage.model.entity.AiRagResultSource;
import org.springframework.stereotype.Component;

@Component
public class ProjectRiskDraftHandler extends AbstractAgentReportDraftHandler {
    private final RagSourceVerifier sourceVerifier;
    public ProjectRiskDraftHandler(ObjectMapper objectMapper, AiAgentRunMapper runMapper,
                                   AiAnalysisReportMapper reportMapper, AiAnalysisReportSourceMapper sourceMapper,
                                   PermissionService permissionService, BusinessDataVersionService versionService,
                                   KnowledgeHashing hashing, RagSourceVerifier sourceVerifier) {
        super(objectMapper, runMapper, reportMapper, sourceMapper, permissionService, versionService, hashing);
        this.sourceVerifier = sourceVerifier;
    }

    @Override public String scene() { return AgentSceneEnum.PROJECT_RISK.getDraftScene(); }
    @Override protected String expectedReportType() { return "PROJECT_RISK"; }
    @Override protected void authorize(Long actorUserId, AgentReportDraftPayload payload) {
        var scope = permissionService.requireProjectView(actorUserId, payload.projectId());
        var stored = payload.sources().stream().map(source -> {
            AiRagResultSource value = new AiRagResultSource();
            value.setSourceType(source.sourceType());
            value.setSourceId(source.sourceId());
            value.setDocumentKey(source.documentKey());
            value.setChunkIndex(source.chunkIndex());
            value.setContentHash(source.contentHash());
            value.setPayloadHash(source.payloadHash());
            return value;
        }).toList();
        if (sourceVerifier.verifyStored(actorUserId, scope, stored) != RagSourceValidationStatus.VALID) {
            throw new com.spt.learningmanage.exception.BusinessException(
                    com.spt.learningmanage.exception.ErrorCode.AGENT_REPORT_STALE, "报告引用已变化");
        }
    }
}
