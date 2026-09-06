package com.spt.learningmanage.service.impl.ai.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.agent.model.AgentReportDraftPayload;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiAgentRunMapper;
import com.spt.learningmanage.mapper.AiAnalysisReportMapper;
import com.spt.learningmanage.mapper.AiAnalysisReportSourceMapper;
import com.spt.learningmanage.model.dto.ai.draft.AgentReportConfirmationContext;
import com.spt.learningmanage.model.entity.AiAgentRun;
import com.spt.learningmanage.model.entity.AiAnalysisReport;
import com.spt.learningmanage.model.entity.AiAnalysisReportSource;
import com.spt.learningmanage.model.entity.AiDraft;
import com.spt.learningmanage.service.BusinessDataVersionService;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.ai.draft.AiDraftHandler;
import com.spt.learningmanage.service.knowledge.KnowledgeHashing;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

abstract class AbstractAgentReportDraftHandler implements AiDraftHandler<AgentReportConfirmationContext> {
    private final ObjectMapper objectMapper;
    private final AiAgentRunMapper runMapper;
    private final AiAnalysisReportMapper reportMapper;
    private final AiAnalysisReportSourceMapper sourceMapper;
    protected final PermissionService permissionService;
    private final BusinessDataVersionService versionService;
    private final KnowledgeHashing hashing;

    protected AbstractAgentReportDraftHandler(ObjectMapper objectMapper,
                                              AiAgentRunMapper runMapper,
                                              AiAnalysisReportMapper reportMapper,
                                              AiAnalysisReportSourceMapper sourceMapper,
                                              PermissionService permissionService,
                                              BusinessDataVersionService versionService,
                                              KnowledgeHashing hashing) {
        this.objectMapper = objectMapper;
        this.runMapper = runMapper;
        this.reportMapper = reportMapper;
        this.sourceMapper = sourceMapper;
        this.permissionService = permissionService;
        this.versionService = versionService;
        this.hashing = hashing;
    }

    @Override public int currentSchemaVersion() { return 1; }
    @Override public Set<Integer> supportedSchemaVersions() { return Set.of(1); }
    @Override public Class<AgentReportConfirmationContext> contextType() { return AgentReportConfirmationContext.class; }

    @Override
    public Long apply(AiDraft draft, AgentReportConfirmationContext context) {
        AgentReportDraftPayload payload = parse(draft);
        if (!expectedReportType().equals(payload.reportType())) {
            throw new BusinessException(ErrorCode.AI_DRAFT_CONFLICT, "报告草稿类型不匹配");
        }
        AiAgentRun run = runMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiAgentRun>()
                .eq(AiAgentRun::getRunId, payload.sourceRunId())
                .eq(AiAgentRun::getUserId, draft.getUserId()).last("limit 1"));
        if (run == null || !java.util.Objects.equals(run.getDraftId(), draft.getDraftId())
                || !("SUCCEEDED".equals(run.getStatus()) || "PARTIAL".equals(run.getStatus()))) {
            throw new BusinessException(ErrorCode.AI_DRAFT_CONFLICT, "Agent Run 与草稿不一致");
        }
        authorize(draft.getUserId(), payload);
        long currentVersion = payload.projectId() != null
                ? versionService.projectVersion(payload.projectId()) : versionService.teamVersion(payload.teamId());
        if (currentVersion != payload.sourceDataVersion()) {
            throw new BusinessException(ErrorCode.AGENT_REPORT_STALE);
        }
        AiAnalysisReport report = new AiAnalysisReport();
        report.setReportId(UUID.randomUUID().toString());
        report.setReportType(payload.reportType());
        report.setSchemaVersion(payload.schemaVersion());
        report.setProjectId(payload.projectId());
        report.setTeamId(payload.teamId());
        report.setCreatorUserId(draft.getUserId());
        report.setSourceRunId(payload.sourceRunId());
        report.setSourceDataVersion(payload.sourceDataVersion());
        report.setManagerSummary(payload.managerSummary());
        report.setPublicSummary(payload.publicSummary());
        report.setMemberMetricsJson(json(payload.memberMetrics()));
        report.setRecommendationsJson(json(payload.recommendations()));
        report.setContentHash(hashing.sha256(draft.getPayloadJson()));
        report.setModel(payload.model());
        report.setPromptCode(payload.promptCode());
        report.setPromptVersion(payload.promptVersion());
        report.setTraceId(payload.traceId());
        report.setGeneratedAt(payload.generatedAt() == null ? LocalDateTime.now() : payload.generatedAt());
        report.setIsDelete(0);
        if (reportMapper.insert(report) != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "分析报告保存失败");
        }
        for (var source : payload.sources()) {
            if (!permissionService.filterReadableTaskIds(draft.getUserId(),
                    "TASK".equals(source.sourceType()) ? Set.of(source.sourceId()) : Set.of()).contains(source.sourceId())
                    && !permissionService.filterReadableWeeklyReviewIds(draft.getUserId(),
                    "WEEKLY_REVIEW".equals(source.sourceType()) ? Set.of(source.sourceId()) : Set.of()).contains(source.sourceId())) {
                throw new BusinessException(ErrorCode.AGENT_REPORT_STALE, "报告引用已失效");
            }
            AiAnalysisReportSource entity = new AiAnalysisReportSource();
            entity.setReportId(report.getReportId());
            entity.setCitationId(source.citationId());
            entity.setSourceType(source.sourceType());
            entity.setSourceId(source.sourceId());
            entity.setDocumentKey(source.documentKey());
            entity.setChunkIndex(source.chunkIndex());
            entity.setContentHash(source.contentHash());
            entity.setPayloadHash(source.payloadHash());
            entity.setTitleSnapshot(source.title());
            sourceMapper.insert(entity);
        }
        return report.getId();
    }

    protected abstract String expectedReportType();
    protected abstract void authorize(Long actorUserId, AgentReportDraftPayload payload);

    private AgentReportDraftPayload parse(AiDraft draft) {
        try {
            return objectMapper.readValue(draft.getPayloadJson(), AgentReportDraftPayload.class);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AI_DRAFT_CONFLICT, "报告草稿内容损坏");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AI_DRAFT_CONFLICT, "报告草稿序列化失败");
        }
    }
}
