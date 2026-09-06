package com.spt.learningmanage.service.rag;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.spt.learningmanage.constant.RagResultStatusEnum;
import com.spt.learningmanage.constant.RagSourceValidationStatus;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiRagResultMapper;
import com.spt.learningmanage.mapper.AiRagResultSourceMapper;
import com.spt.learningmanage.model.entity.AiRagResult;
import com.spt.learningmanage.model.entity.AiRagResultSource;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.model.rag.PersistedRagResult;
import com.spt.learningmanage.model.vo.rag.RagAnswerVO;
import com.spt.learningmanage.model.vo.rag.RagSourceVO;
import com.spt.learningmanage.service.PermissionService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class RagResultViewService {
    private final AiRagResultMapper resultMapper;
    private final AiRagResultSourceMapper sourceMapper;
    private final PermissionService permissionService;
    private final RagSourceVerifier sourceVerifier;

    public RagResultViewService(AiRagResultMapper resultMapper,
                                AiRagResultSourceMapper sourceMapper,
                                PermissionService permissionService,
                                RagSourceVerifier sourceVerifier) {
        this.resultMapper = resultMapper;
        this.sourceMapper = sourceMapper;
        this.permissionService = permissionService;
        this.sourceVerifier = sourceVerifier;
    }

    public RagAnswerVO get(Long actorUserId, String requestId) {
        AiRagResult result = resultMapper.selectOne(new QueryWrapper<AiRagResult>()
                .eq("request_id", requestId)
                .eq("user_id", actorUserId)
                .last("limit 1"));
        if (result == null) {
            throw new BusinessException(ErrorCode.RAG_RESULT_NOT_FOUND);
        }
        if (RagResultStatusEnum.INVALIDATED.name().equals(result.getStatus())) {
            throw new BusinessException(ErrorCode.RAG_RESULT_INVALIDATED);
        }
        if (RagResultStatusEnum.EXPIRED.name().equals(result.getStatus())
                || !result.getExpiresAt().isAfter(LocalDateTime.now())) {
            transition(result.getId(), RagResultStatusEnum.EXPIRED);
            throw new BusinessException(ErrorCode.RAG_RESULT_EXPIRED);
        }

        ProjectAccessScope scope;
        try {
            scope = permissionService.requireProjectView(actorUserId, result.getProjectId());
        } catch (BusinessException exception) {
            transition(result.getId(), RagResultStatusEnum.INVALIDATED);
            throw exception;
        }
        List<AiRagResultSource> sources = sources(result.getId());
        RagSourceValidationStatus validation = sourceVerifier.verifyStored(actorUserId, scope, sources);
        if (validation == RagSourceValidationStatus.INVALIDATED
                || (sources.isEmpty() && !enabled(result.getInsufficientEvidence()))) {
            transition(result.getId(), RagResultStatusEnum.INVALIDATED);
            throw new BusinessException(ErrorCode.RAG_RESULT_INVALIDATED);
        }
        if (validation == RagSourceValidationStatus.STALE) {
            transition(result.getId(), RagResultStatusEnum.STALE);
            result.setStatus(RagResultStatusEnum.STALE.name());
            return toVO(result, sources, false);
        }
        touchValidation(result.getId());
        return toVO(result, sources, !RagResultStatusEnum.STALE.name().equals(result.getStatus()));
    }

    public RagAnswerVO toVO(PersistedRagResult persisted) {
        return toVO(persisted.result(), persisted.sources(), true);
    }

    private RagAnswerVO toVO(AiRagResult result,
                             List<AiRagResultSource> sources,
                             boolean includeAnswer) {
        RagAnswerVO vo = new RagAnswerVO();
        vo.setRequestId(result.getRequestId());
        vo.setStatus(result.getStatus());
        vo.setAnswer(includeAnswer ? result.getAnswerText() : null);
        vo.setInsufficientEvidence(enabled(result.getInsufficientEvidence()));
        vo.setDegraded(enabled(result.getDegraded()));
        vo.setDegradationReason(result.getDegradationReason());
        vo.setKnowledgeAsOf(result.getKnowledgeAsOf());
        vo.setSources(sources.stream().map(this::toSourceVO).toList());
        return vo;
    }

    private RagSourceVO toSourceVO(AiRagResultSource source) {
        RagSourceVO vo = new RagSourceVO();
        vo.setCitationId(source.getCitationId());
        vo.setSourceType(source.getSourceType());
        vo.setSourceId(source.getSourceId());
        vo.setTitle(source.getTitleSnapshot());
        vo.setVectorScore(source.getVectorScore());
        vo.setRerankScore(source.getRerankScore());
        vo.setScore(source.getRerankScore() == null ? source.getVectorScore() : source.getRerankScore());
        vo.setUpdatedAt(source.getSourceUpdatedAt());
        return vo;
    }

    private List<AiRagResultSource> sources(Long resultId) {
        List<AiRagResultSource> values = new java.util.ArrayList<>(sourceMapper.selectList(
                new QueryWrapper<AiRagResultSource>()
                        .eq("result_id", resultId)));
        values.sort(Comparator.comparingInt(value -> citationNumber(value.getCitationId())));
        return values;
    }

    private int citationNumber(String citationId) {
        try {
            return Integer.parseInt(citationId.substring(1));
        } catch (RuntimeException exception) {
            return Integer.MAX_VALUE;
        }
    }

    private void transition(Long id, RagResultStatusEnum target) {
        resultMapper.update(null, new UpdateWrapper<AiRagResult>()
                .eq("id", id)
                .in("status",
                        RagResultStatusEnum.ACTIVE.name(), RagResultStatusEnum.STALE.name())
                .set("status", target.name()));
    }

    private void touchValidation(Long id) {
        resultMapper.update(null, new UpdateWrapper<AiRagResult>()
                .eq("id", id)
                .eq("status", RagResultStatusEnum.ACTIVE.name())
                .set("update_time", LocalDateTime.now()));
    }

    private boolean enabled(Integer value) {
        return value != null && value == 1;
    }
}
