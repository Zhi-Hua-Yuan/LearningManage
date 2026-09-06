package com.spt.learningmanage.service.rag;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.spt.learningmanage.config.EmbeddingProperties;
import com.spt.learningmanage.config.RagProperties;
import com.spt.learningmanage.config.RerankProperties;
import com.spt.learningmanage.constant.RagQueryStatusEnum;
import com.spt.learningmanage.mapper.AiRagQueryLogMapper;
import com.spt.learningmanage.model.entity.AiRagQueryLog;
import com.spt.learningmanage.model.rag.RagRetrievalOutcome;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class RagQueryAuditService {
    private final AiRagQueryLogMapper mapper;
    private final RagProperties rag;
    private final EmbeddingProperties embedding;
    private final RerankProperties rerank;

    public RagQueryAuditService(AiRagQueryLogMapper mapper,
                                RagProperties rag,
                                EmbeddingProperties embedding,
                                RerankProperties rerank) {
        this.mapper = mapper;
        this.rag = rag;
        this.embedding = embedding;
        this.rerank = rerank;
    }

    public AiRagQueryLog start(String requestId,
                               Long userId,
                               Long projectId,
                               String questionHmac,
                               String traceId) {
        AiRagQueryLog log = new AiRagQueryLog();
        log.setRequestId(requestId);
        log.setUserId(userId);
        log.setProjectId(projectId);
        log.setQuestionHmac(questionHmac);
        log.setStatus(RagQueryStatusEnum.RUNNING.name());
        log.setRetrievalConfigVersion(rag.getRetrievalConfigVersion());
        log.setEmbeddingModel(embedding.getModel());
        log.setEmbeddingDimension(embedding.getDimension());
        log.setRerankModel(rerank.getModel());
        log.setInitialTopK(rag.getInitialTopK());
        log.setFinalTopK(rag.getFinalTopK());
        log.setVectorThreshold(BigDecimal.valueOf(rag.getVectorScoreThreshold()));
        log.setRerankThreshold(BigDecimal.valueOf(rag.getRerankScoreThreshold()));
        log.setCandidateCount(0);
        log.setAuthorizedCount(0);
        log.setFinalCount(0);
        log.setDegraded(0);
        log.setTraceId(traceId);
        mapper.insert(log);
        return log;
    }

    public void complete(Long id,
                         RagQueryStatusEnum status,
                         RagRetrievalOutcome outcome,
                         long durationMs) {
        int rows = mapper.update(null, new LambdaUpdateWrapper<AiRagQueryLog>()
                .eq(AiRagQueryLog::getId, id)
                .eq(AiRagQueryLog::getStatus, RagQueryStatusEnum.RUNNING.name())
                .set(AiRagQueryLog::getStatus, status.name())
                .set(AiRagQueryLog::getEmbeddingModel, outcome.embeddingModel())
                .set(AiRagQueryLog::getRerankModel, outcome.rerankModel())
                .set(AiRagQueryLog::getCandidateCount, outcome.vectorCandidateCount())
                .set(AiRagQueryLog::getAuthorizedCount, outcome.authorizedCandidateCount())
                .set(AiRagQueryLog::getFinalCount, outcome.candidates().size())
                .set(AiRagQueryLog::getDegraded, outcome.degraded() ? 1 : 0)
                .set(AiRagQueryLog::getDurationMs, durationMs));
        if (rows != 1) {
            throw new IllegalStateException("RAG query log terminal transition lost");
        }
    }

    public void fail(Long id, String failureType, long durationMs) {
        mapper.update(null, new LambdaUpdateWrapper<AiRagQueryLog>()
                .eq(AiRagQueryLog::getId, id)
                .eq(AiRagQueryLog::getStatus, RagQueryStatusEnum.RUNNING.name())
                .set(AiRagQueryLog::getStatus, RagQueryStatusEnum.FAILED.name())
                .set(AiRagQueryLog::getFailureType, safeFailureType(failureType))
                .set(AiRagQueryLog::getDurationMs, durationMs));
    }

    private String safeFailureType(String value) {
        if (value == null || value.isBlank()) {
            return "INTERNAL";
        }
        String normalized = value.replaceAll("[^A-Za-z0-9_.-]", "_");
        return normalized.substring(0, Math.min(normalized.length(), 64));
    }
}
