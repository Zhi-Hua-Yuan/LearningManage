package com.spt.learningmanage.service.rag;

import com.spt.learningmanage.config.RagProperties;
import com.spt.learningmanage.constant.RagResultStatusEnum;
import com.spt.learningmanage.constant.RagQueryStatusEnum;
import com.spt.learningmanage.mapper.AiRagResultMapper;
import com.spt.learningmanage.mapper.AiRagResultSourceMapper;
import com.spt.learningmanage.model.entity.AiRagQueryLog;
import com.spt.learningmanage.model.entity.AiRagResult;
import com.spt.learningmanage.model.entity.AiRagResultSource;
import com.spt.learningmanage.model.rag.PersistedRagResult;
import com.spt.learningmanage.model.rag.RagAnswerContent;
import com.spt.learningmanage.model.rag.RagCandidate;
import com.spt.learningmanage.model.rag.RagContext;
import com.spt.learningmanage.model.rag.RagGeneratedAnswer;
import com.spt.learningmanage.model.rag.RagRetrievalOutcome;
import com.spt.learningmanage.service.knowledge.KnowledgeHashing;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class RagResultPersistenceService {
    private final AiRagResultMapper resultMapper;
    private final AiRagResultSourceMapper sourceMapper;
    private final RagProperties properties;
    private final KnowledgeHashing hashing;
    private final RagQueryAuditService auditService;

    public RagResultPersistenceService(AiRagResultMapper resultMapper,
                                       AiRagResultSourceMapper sourceMapper,
                                       RagProperties properties,
                                       KnowledgeHashing hashing,
                                       RagQueryAuditService auditService) {
        this.resultMapper = resultMapper;
        this.sourceMapper = sourceMapper;
        this.properties = properties;
        this.hashing = hashing;
        this.auditService = auditService;
    }

    @Transactional(rollbackFor = Exception.class)
    public PersistedRagResult save(String requestId,
                                   Long userId,
                                   Long projectId,
                                   String traceId,
                                   AiRagQueryLog queryLog,
                                   RagRetrievalOutcome retrieval,
                                   RagContext context,
                                   RagGeneratedAnswer generated,
                                   long durationMs) {
        RagAnswerContent answer = generated.content();
        LocalDateTime now = LocalDateTime.now();
        AiRagResult result = new AiRagResult();
        result.setRequestId(requestId);
        result.setQueryLogId(queryLog.getId());
        result.setUserId(userId);
        result.setProjectId(projectId);
        result.setAnswerText(answer.answer());
        result.setAnswerHash(hashing.sha256(answer.answer()));
        result.setStatus(RagResultStatusEnum.ACTIVE.name());
        result.setInsufficientEvidence(answer.insufficientEvidence() ? 1 : 0);
        result.setDegraded(retrieval.degraded() || generated.degraded() ? 1 : 0);
        result.setDegradationReason(joinReasons(retrieval.degradationReason(), generated.degradationReason()));
        result.setAiCallLogId(generated.aiCallLogId());
        result.setModel(generated.actualModel());
        result.setPromptCode(generated.promptCode());
        result.setPromptVersion(generated.promptVersion());
        result.setRetrievalConfigVersion(properties.getRetrievalConfigVersion());
        result.setKnowledgeAsOf(now);
        result.setTraceId(traceId);
        result.setExpiresAt(now.plusDays(properties.getResultRetentionDays()));
        resultMapper.insert(result);

        List<AiRagResultSource> sources = new ArrayList<>();
        for (String citationId : answer.citations()) {
            RagCandidate candidate = context.evidence().get(citationId);
            if (candidate == null) {
                throw new IllegalStateException("Validated citation lost its evidence mapping");
            }
            AiRagResultSource source = new AiRagResultSource();
            source.setResultId(result.getId());
            source.setCitationId(citationId);
            source.setSourceType(candidate.sourceType().name());
            source.setSourceId(candidate.sourceId());
            source.setDocumentKey(candidate.documentKey());
            source.setChunkIndex(candidate.chunkIndex());
            source.setContentHash(candidate.contentHash());
            source.setPayloadHash(candidate.payloadHash());
            source.setVectorScore(candidate.vectorScore());
            source.setRerankScore(candidate.rerankScore());
            source.setTitleSnapshot(candidate.title());
            source.setSourceUpdatedAt(candidate.sourceUpdatedAt());
            sourceMapper.insert(source);
            sources.add(source);
        }
        RagQueryStatusEnum queryStatus = answer.insufficientEvidence()
                ? RagQueryStatusEnum.INSUFFICIENT : RagQueryStatusEnum.SUCCEEDED;
        auditService.complete(queryLog.getId(), queryStatus, retrieval, durationMs);
        return new PersistedRagResult(result, sources);
    }

    private String joinReasons(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        String joined = first + "; " + second;
        return joined.substring(0, Math.min(joined.length(), 500));
    }
}
