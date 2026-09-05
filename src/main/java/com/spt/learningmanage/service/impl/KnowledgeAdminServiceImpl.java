package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.config.EmbeddingProperties;
import com.spt.learningmanage.config.KnowledgeIndexProperties;
import com.spt.learningmanage.config.QdrantProperties;
import com.spt.learningmanage.constant.KnowledgeBackfillRunTypeEnum;
import com.spt.learningmanage.constant.KnowledgeBackfillStatusEnum;
import com.spt.learningmanage.constant.KnowledgeDocumentStatusEnum;
import com.spt.learningmanage.constant.KnowledgeEventStatusEnum;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiKnowledgeBackfillRunMapper;
import com.spt.learningmanage.mapper.AiKnowledgeDocumentMapper;
import com.spt.learningmanage.mapper.AiKnowledgeIndexEventMapper;
import com.spt.learningmanage.model.dto.knowledge.KnowledgeBackfillCreateRequest;
import com.spt.learningmanage.model.dto.knowledge.KnowledgeEventQueryRequest;
import com.spt.learningmanage.model.entity.AiKnowledgeBackfillRun;
import com.spt.learningmanage.model.entity.AiKnowledgeDocument;
import com.spt.learningmanage.model.entity.AiKnowledgeIndexEvent;
import com.spt.learningmanage.model.vo.knowledge.KnowledgeBackfillVO;
import com.spt.learningmanage.model.vo.knowledge.KnowledgeEventVO;
import com.spt.learningmanage.model.vo.knowledge.KnowledgeIndexStatusVO;
import com.spt.learningmanage.service.KnowledgeAdminService;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.trace.TraceContext;
import com.spt.learningmanage.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class KnowledgeAdminServiceImpl implements KnowledgeAdminService {

    private static final Pattern RUN_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,64}");

    private final AiKnowledgeIndexEventMapper eventMapper;
    private final AiKnowledgeDocumentMapper documentMapper;
    private final AiKnowledgeBackfillRunMapper backfillMapper;
    private final PermissionService permissionService;
    private final KnowledgeIndexProperties indexProperties;
    private final EmbeddingProperties embeddingProperties;
    private final QdrantProperties qdrantProperties;

    public KnowledgeAdminServiceImpl(AiKnowledgeIndexEventMapper eventMapper,
                                     AiKnowledgeDocumentMapper documentMapper,
                                     AiKnowledgeBackfillRunMapper backfillMapper,
                                     PermissionService permissionService,
                                     KnowledgeIndexProperties indexProperties,
                                     EmbeddingProperties embeddingProperties,
                                     QdrantProperties qdrantProperties) {
        this.eventMapper = eventMapper;
        this.documentMapper = documentMapper;
        this.backfillMapper = backfillMapper;
        this.permissionService = permissionService;
        this.indexProperties = indexProperties;
        this.embeddingProperties = embeddingProperties;
        this.qdrantProperties = qdrantProperties;
    }

    @Override
    public KnowledgeIndexStatusVO status() {
        requireAdmin();
        KnowledgeIndexStatusVO result = new KnowledgeIndexStatusVO();
        result.setWorkerEnabled(indexProperties.isWorkerEnabled());
        result.setEmbeddingModel(embeddingProperties.getModel());
        result.setEmbeddingDimension(embeddingProperties.getDimension());
        result.setCollection(qdrantProperties.getCollection());
        result.setAlias(qdrantProperties.getAlias());
        result.setEventCounts(eventCounts());
        result.setDocumentCounts(documentCounts());
        result.setBackfillCounts(backfillCounts());
        return result;
    }

    @Override
    public Page<KnowledgeEventVO> listEvents(KnowledgeEventQueryRequest request) {
        requireAdmin();
        KnowledgeEventQueryRequest valid = request == null ? new KnowledgeEventQueryRequest() : request;
        long current = valid.getCurrent() == null || valid.getCurrent() < 1 ? 1 : valid.getCurrent();
        long size = valid.getSize() == null ? 20 : Math.max(1, Math.min(valid.getSize(), 100));
        LambdaQueryWrapper<AiKnowledgeIndexEvent> query = new LambdaQueryWrapper<>();
        if (valid.getStatus() != null && !valid.getStatus().isBlank()) {
            String status = valid.getStatus().trim().toUpperCase(Locale.ROOT);
            try {
                KnowledgeEventStatusEnum.valueOf(status);
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "status 不合法");
            }
            query.eq(AiKnowledgeIndexEvent::getStatus, status);
        }
        query.orderByDesc(AiKnowledgeIndexEvent::getId);
        Page<AiKnowledgeIndexEvent> page = eventMapper.selectPage(new Page<>(current, size), query);
        Page<KnowledgeEventVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toEventVO).toList());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean replayEvent(Long eventId) {
        requireAdmin();
        if (eventId == null || eventId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "eventId 不合法");
        }
        AiKnowledgeIndexEvent event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_EVENT_NOT_FOUND);
        }
        if (!KnowledgeEventStatusEnum.DEAD.name().equals(event.getStatus())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_EVENT_NOT_REPLAYABLE);
        }
        int rows = eventMapper.update(null, new UpdateWrapper<AiKnowledgeIndexEvent>()
                .eq("id", eventId)
                .eq("status", KnowledgeEventStatusEnum.DEAD.name())
                .set("status", KnowledgeEventStatusEnum.PENDING.name())
                .set("attempt_count", 0)
                .set("next_attempt_at", null)
                .set("claimed_by", null)
                .set("claim_token", null)
                .set("claimed_at", null)
                .set("lease_until", null)
                .set("failure_type", null)
                .set("last_error", null)
                .set("trace_id", TraceContext.currentOrCreate()));
        if (rows != 1) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_EVENT_NOT_REPLAYABLE);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBackfillVO createBackfill(KnowledgeBackfillCreateRequest request) {
        requireAdmin();
        if (!indexProperties.isWorkerEnabled()) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_INDEX_DISABLED);
        }
        ValidBackfill valid = validateBackfill(request);
        AiKnowledgeBackfillRun existing = backfillMapper.selectOne(
                new LambdaQueryWrapper<AiKnowledgeBackfillRun>()
                        .eq(AiKnowledgeBackfillRun::getRunKey, valid.runKey())
                        .last("limit 1"));
        if (existing != null) {
            return toBackfillVO(existing, true);
        }
        AiKnowledgeBackfillRun run = new AiKnowledgeBackfillRun();
        run.setRunKey(valid.runKey());
        run.setRunType(valid.runType().name());
        run.setSourceScope(valid.sourceScope());
        run.setBatchSize(valid.batchSize());
        run.setStatus(KnowledgeBackfillStatusEnum.PENDING.name());
        run.setCursorTaskId(0L);
        run.setCursorReviewId(0L);
        run.setDiscoveredCount(0L);
        run.setEnqueuedCount(0L);
        run.setSuccessCount(0L);
        run.setFailedCount(0L);
        run.setDeadCount(0L);
        run.setTraceId(TraceContext.currentOrCreate());
        try {
            if (backfillMapper.insert(run) != 1) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建回填任务失败");
            }
        } catch (DuplicateKeyException exception) {
            AiKnowledgeBackfillRun winner = backfillMapper.selectOne(
                    new LambdaQueryWrapper<AiKnowledgeBackfillRun>()
                            .eq(AiKnowledgeBackfillRun::getRunKey, valid.runKey()).last("limit 1"));
            if (winner == null) {
                throw new BusinessException(ErrorCode.KNOWLEDGE_BACKFILL_CONFLICT);
            }
            return toBackfillVO(winner, true);
        }
        return toBackfillVO(run, false);
    }

    @Override
    public KnowledgeBackfillVO getBackfill(Long runId) {
        requireAdmin();
        if (runId == null || runId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "runId 不合法");
        }
        AiKnowledgeBackfillRun run = backfillMapper.selectById(runId);
        if (run == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BACKFILL_NOT_FOUND);
        }
        return toBackfillVO(run, false);
    }

    private ValidBackfill validateBackfill(KnowledgeBackfillCreateRequest request) {
        if (request == null || request.getRunKey() == null
                || !RUN_KEY.matcher(request.getRunKey().trim()).matches()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "runKey 不合法");
        }
        try {
            KnowledgeBackfillRunTypeEnum runType = KnowledgeBackfillRunTypeEnum.valueOf(
                    normalize(request.getRunType(), "INITIAL"));
            String scope = normalize(request.getSourceScope(), "ALL");
            if (!"ALL".equals(scope)) {
                KnowledgeSourceTypeEnum.valueOf(scope);
            }
            int batchSize = request.getBatchSize() == null ? 500 : request.getBatchSize();
            if (batchSize < 100 || batchSize > 1000) {
                throw new IllegalArgumentException();
            }
            return new ValidBackfill(request.getRunKey().trim(), runType, scope, batchSize);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "回填参数不合法");
        }
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    private void requireAdmin() {
        Long actor = UserHolder.get();
        if (actor == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        permissionService.requireSystemAdmin(actor);
    }

    private Map<String, Long> eventCounts() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (KnowledgeEventStatusEnum status : KnowledgeEventStatusEnum.values()) {
            result.put(status.name(), eventMapper.selectCount(new LambdaQueryWrapper<AiKnowledgeIndexEvent>()
                    .eq(AiKnowledgeIndexEvent::getStatus, status.name())));
        }
        return result;
    }

    private Map<String, Long> documentCounts() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (KnowledgeDocumentStatusEnum status : KnowledgeDocumentStatusEnum.values()) {
            result.put(status.name(), documentMapper.selectCount(new LambdaQueryWrapper<AiKnowledgeDocument>()
                    .eq(AiKnowledgeDocument::getStatus, status.name())));
        }
        return result;
    }

    private Map<String, Long> backfillCounts() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (KnowledgeBackfillStatusEnum status : KnowledgeBackfillStatusEnum.values()) {
            result.put(status.name(), backfillMapper.selectCount(new LambdaQueryWrapper<AiKnowledgeBackfillRun>()
                    .eq(AiKnowledgeBackfillRun::getStatus, status.name())));
        }
        return result;
    }

    private KnowledgeEventVO toEventVO(AiKnowledgeIndexEvent event) {
        KnowledgeEventVO vo = new KnowledgeEventVO();
        vo.setEventId(event.getId());
        vo.setSourceType(event.getSourceType());
        vo.setSourceId(event.getSourceId());
        vo.setEventType(event.getEventType());
        vo.setStatus(event.getStatus());
        vo.setAttemptCount(event.getAttemptCount());
        vo.setFailureType(event.getFailureType());
        vo.setLastError(event.getLastError());
        vo.setTraceId(event.getTraceId());
        vo.setNextAttemptAt(event.getNextAttemptAt());
        vo.setCreateTime(event.getCreateTime());
        vo.setUpdateTime(event.getUpdateTime());
        return vo;
    }

    private KnowledgeBackfillVO toBackfillVO(AiKnowledgeBackfillRun run, boolean replay) {
        KnowledgeBackfillVO vo = new KnowledgeBackfillVO();
        BeanUtils.copyProperties(run, vo);
        vo.setRunId(run.getId());
        vo.setIdempotentReplay(replay);
        return vo;
    }

    private record ValidBackfill(String runKey,
                                 KnowledgeBackfillRunTypeEnum runType,
                                 String sourceScope,
                                 int batchSize) {
    }
}
