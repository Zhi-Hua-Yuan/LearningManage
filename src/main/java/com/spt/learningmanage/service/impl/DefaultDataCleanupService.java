package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.spt.learningmanage.constant.CleanupResourceTypeEnum;
import com.spt.learningmanage.mapper.*;
import com.spt.learningmanage.model.entity.*;
import com.spt.learningmanage.model.ops.CleanupBatchResult;
import com.spt.learningmanage.service.DataCleanupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class DefaultDataCleanupService implements DataCleanupService {
    private static final Set<String> AGENT_TERMINAL = Set.of(
            "SUCCEEDED", "PARTIAL", "FAILED", "TIMED_OUT", "CANCELED");

    private final com.spt.learningmanage.service.AiCallLogOperationsService callLogOperations;
    private final AiRagQueryLogMapper ragQueryMapper;
    private final AiRagResultMapper ragResultMapper;
    private final AiRagResultSourceMapper ragSourceMapper;
    private final AiAgentRunMapper agentRunMapper;
    private final AiAgentToolLogMapper toolLogMapper;
    private final AiKnowledgeIndexEventMapper eventMapper;
    private final AiDraftMapper draftMapper;
    private final AiAnalysisReportMapper reportMapper;
    private final AiAnalysisReportSourceMapper reportSourceMapper;
    private final AiAdminOperationLogMapper adminLogMapper;

    public DefaultDataCleanupService(com.spt.learningmanage.service.AiCallLogOperationsService callLogOperations,
                                     AiRagQueryLogMapper ragQueryMapper,
                                     AiRagResultMapper ragResultMapper,
                                     AiRagResultSourceMapper ragSourceMapper,
                                     AiAgentRunMapper agentRunMapper,
                                     AiAgentToolLogMapper toolLogMapper,
                                     AiKnowledgeIndexEventMapper eventMapper,
                                     AiDraftMapper draftMapper,
                                     AiAnalysisReportMapper reportMapper,
                                     AiAnalysisReportSourceMapper reportSourceMapper,
                                     AiAdminOperationLogMapper adminLogMapper) {
        this.callLogOperations = callLogOperations;
        this.ragQueryMapper = ragQueryMapper;
        this.ragResultMapper = ragResultMapper;
        this.ragSourceMapper = ragSourceMapper;
        this.agentRunMapper = agentRunMapper;
        this.toolLogMapper = toolLogMapper;
        this.eventMapper = eventMapper;
        this.draftMapper = draftMapper;
        this.reportMapper = reportMapper;
        this.reportSourceMapper = reportSourceMapper;
        this.adminLogMapper = adminLogMapper;
    }

    @Override
    public long estimate(CleanupResourceTypeEnum type, LocalDateTime cutoff) {
        return switch (type) {
            case AI_CALL_BODY -> callLogOperations.countBodyCleanupCandidates(cutoff);
            case AI_CALL_METADATA -> callLogOperations.countMetadataCleanupCandidates(cutoff);
            case RAG_RESULT_BODY -> ragResultMapper.selectCount(new LambdaQueryWrapper<AiRagResult>()
                    .isNull(AiRagResult::getBodyPurgedAt).lt(AiRagResult::getCreateTime, cutoff));
            case RAG_HISTORY -> ragQueryMapper.selectCount(new LambdaQueryWrapper<AiRagQueryLog>()
                    .ne(AiRagQueryLog::getStatus, "RUNNING")
                    .lt(AiRagQueryLog::getCreateTime, cutoff));
            case AGENT_HISTORY -> agentRunMapper.selectCount(new LambdaQueryWrapper<AiAgentRun>()
                    .in(AiAgentRun::getStatus, AGENT_TERMINAL).lt(AiAgentRun::getCreateTime, cutoff));
            case KNOWLEDGE_EVENT -> eventMapper.selectCount(new LambdaQueryWrapper<AiKnowledgeIndexEvent>()
                    .eq(AiKnowledgeIndexEvent::getStatus, "SUCCESS")
                    .lt(AiKnowledgeIndexEvent::getCreateTime, cutoff));
            case DRAFT_PAYLOAD -> draftMapper.selectCount(new LambdaQueryWrapper<AiDraft>()
                    .in(AiDraft::getStatus, 1, 2, 3).isNull(AiDraft::getPayloadPurgedAt)
                    .lt(AiDraft::getUpdateTime, cutoff));
            case DELETED_REPORT -> reportMapper.countDeletedForCleanup(cutoff);
            case ADMIN_AUDIT -> adminLogMapper.selectCount(new LambdaQueryWrapper<AiAdminOperationLog>()
                    .lt(AiAdminOperationLog::getCreateTime, cutoff));
        };
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CleanupBatchResult processBatch(CleanupResourceTypeEnum type, LocalDateTime cutoff,
                                           long cursor, int batchSize) {
        return switch (type) {
            case AI_CALL_BODY -> purgeCallBodies(cutoff, cursor, batchSize);
            case AI_CALL_METADATA -> deleteCallMetadata(cutoff, cursor, batchSize);
            case RAG_RESULT_BODY -> purgeRagBodies(cutoff, cursor, batchSize);
            case RAG_HISTORY -> deleteRagHistory(cutoff, cursor, batchSize);
            case AGENT_HISTORY -> deleteAgentHistory(cutoff, cursor, batchSize);
            case KNOWLEDGE_EVENT -> deleteKnowledgeEvents(cutoff, cursor, batchSize);
            case DRAFT_PAYLOAD -> purgeDraftPayloads(cutoff, cursor, batchSize);
            case DELETED_REPORT -> purgeDeletedReports(cutoff, cursor, batchSize);
            case ADMIN_AUDIT -> deleteAdminAudit(cutoff, cursor, batchSize);
        };
    }

    private CleanupBatchResult purgeCallBodies(LocalDateTime cutoff, long cursor, int size) {
        return callLogOperations.purgeBodyBatch(cutoff, cursor, size);
    }

    private CleanupBatchResult deleteCallMetadata(LocalDateTime cutoff, long cursor, int size) {
        return callLogOperations.deleteMetadataBatch(cutoff, cursor, size);
    }

    private CleanupBatchResult purgeRagBodies(LocalDateTime cutoff, long cursor, int size) {
        List<Long> ids = ragResultMapper.selectList(new LambdaQueryWrapper<AiRagResult>()
                .select(AiRagResult::getId).gt(AiRagResult::getId, cursor)
                .isNull(AiRagResult::getBodyPurgedAt).lt(AiRagResult::getCreateTime, cutoff)
                .orderByAsc(AiRagResult::getId).last("limit " + size)).stream().map(AiRagResult::getId).toList();
        int affected = ids.isEmpty() ? 0 : ragResultMapper.update(null, new LambdaUpdateWrapper<AiRagResult>()
                .in(AiRagResult::getId, ids).isNull(AiRagResult::getBodyPurgedAt)
                .set(AiRagResult::getAnswerText, null).set(AiRagResult::getStatus, "EXPIRED")
                .set(AiRagResult::getBodyPurgedAt, LocalDateTime.now()));
        return result(ids, affected, affected, 0, size);
    }

    private CleanupBatchResult deleteRagHistory(LocalDateTime cutoff, long cursor, int size) {
        List<AiRagQueryLog> rows = ragQueryMapper.selectList(new LambdaQueryWrapper<AiRagQueryLog>()
                .select(AiRagQueryLog::getId).gt(AiRagQueryLog::getId, cursor)
                .ne(AiRagQueryLog::getStatus, "RUNNING")
                .lt(AiRagQueryLog::getCreateTime, cutoff).orderByAsc(AiRagQueryLog::getId).last("limit " + size));
        List<Long> ids = rows.stream().map(AiRagQueryLog::getId).toList();
        if (!ids.isEmpty()) {
            List<Long> resultIds = ragResultMapper.selectList(new LambdaQueryWrapper<AiRagResult>()
                    .select(AiRagResult::getId).in(AiRagResult::getQueryLogId, ids))
                    .stream().map(AiRagResult::getId).toList();
            if (!resultIds.isEmpty()) {
                ragSourceMapper.delete(new LambdaQueryWrapper<AiRagResultSource>()
                        .in(AiRagResultSource::getResultId, resultIds));
                ragResultMapper.deleteByIds(resultIds);
            }
            ragQueryMapper.deleteByIds(ids);
        }
        return result(ids, ids.size(), 0, ids.size(), size);
    }

    private CleanupBatchResult deleteAgentHistory(LocalDateTime cutoff, long cursor, int size) {
        List<AiAgentRun> rows = agentRunMapper.selectList(new LambdaQueryWrapper<AiAgentRun>()
                .select(AiAgentRun::getId, AiAgentRun::getRunId).gt(AiAgentRun::getId, cursor)
                .in(AiAgentRun::getStatus, AGENT_TERMINAL).lt(AiAgentRun::getCreateTime, cutoff)
                .orderByAsc(AiAgentRun::getId).last("limit " + size));
        List<Long> ids = rows.stream().map(AiAgentRun::getId).toList();
        if (!ids.isEmpty()) {
            toolLogMapper.delete(new LambdaQueryWrapper<AiAgentToolLog>()
                    .in(AiAgentToolLog::getRunId, rows.stream().map(AiAgentRun::getRunId).toList()));
            agentRunMapper.deleteByIds(ids);
        }
        return result(ids, ids.size(), 0, ids.size(), size);
    }

    private CleanupBatchResult deleteKnowledgeEvents(LocalDateTime cutoff, long cursor, int size) {
        List<Long> ids = eventMapper.selectList(new LambdaQueryWrapper<AiKnowledgeIndexEvent>()
                .select(AiKnowledgeIndexEvent::getId).gt(AiKnowledgeIndexEvent::getId, cursor)
                .eq(AiKnowledgeIndexEvent::getStatus, "SUCCESS").lt(AiKnowledgeIndexEvent::getCreateTime, cutoff)
                .orderByAsc(AiKnowledgeIndexEvent::getId).last("limit " + size))
                .stream().map(AiKnowledgeIndexEvent::getId).toList();
        int affected = ids.isEmpty() ? 0 : eventMapper.deleteByIds(ids);
        return result(ids, affected, 0, affected, size);
    }

    private CleanupBatchResult purgeDraftPayloads(LocalDateTime cutoff, long cursor, int size) {
        List<Long> ids = draftMapper.selectList(new LambdaQueryWrapper<AiDraft>()
                .select(AiDraft::getId).gt(AiDraft::getId, cursor).in(AiDraft::getStatus, 1, 2, 3)
                .isNull(AiDraft::getPayloadPurgedAt).lt(AiDraft::getUpdateTime, cutoff)
                .orderByAsc(AiDraft::getId).last("limit " + size)).stream().map(AiDraft::getId).toList();
        int affected = ids.isEmpty() ? 0 : draftMapper.update(null, new LambdaUpdateWrapper<AiDraft>()
                .in(AiDraft::getId, ids).isNull(AiDraft::getPayloadPurgedAt)
                .set(AiDraft::getPayloadJson, null).set(AiDraft::getPayloadPurgedAt, LocalDateTime.now()));
        return result(ids, affected, affected, 0, size);
    }

    private CleanupBatchResult purgeDeletedReports(LocalDateTime cutoff, long cursor, int size) {
        List<AiAnalysisReport> rows = reportMapper.selectDeletedForCleanup(cutoff, cursor, size);
        List<Long> ids = rows.stream().map(AiAnalysisReport::getId).toList();
        if (!rows.isEmpty()) {
            reportSourceMapper.delete(new LambdaQueryWrapper<AiAnalysisReportSource>()
                    .in(AiAnalysisReportSource::getReportId,
                            rows.stream().map(AiAnalysisReport::getReportId).toList()));
        }
        int affected = ids.isEmpty() ? 0 : reportMapper.purgeDeletedContent(ids);
        return result(ids, affected, affected, 0, size);
    }

    private CleanupBatchResult deleteAdminAudit(LocalDateTime cutoff, long cursor, int size) {
        List<Long> ids = adminLogMapper.selectList(new LambdaQueryWrapper<AiAdminOperationLog>()
                .select(AiAdminOperationLog::getId).gt(AiAdminOperationLog::getId, cursor)
                .lt(AiAdminOperationLog::getCreateTime, cutoff)
                .orderByAsc(AiAdminOperationLog::getId).last("limit " + size))
                .stream().map(AiAdminOperationLog::getId).toList();
        int affected = ids.isEmpty() ? 0 : adminLogMapper.deleteByIds(ids);
        return result(ids, affected, 0, affected, size);
    }

    private CleanupBatchResult result(List<Long> ids, long affected, long redacted, long deleted, int size) {
        long next = ids.isEmpty() ? 0L : ids.get(ids.size() - 1);
        return new CleanupBatchResult(ids.size(), affected, redacted, deleted, next, ids.size() < size);
    }
}
