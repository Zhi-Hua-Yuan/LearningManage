package com.spt.learningmanage.service.rag;

import com.spt.learningmanage.config.RagProperties;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.exception.KnowledgeIndexException;
import com.spt.learningmanage.exception.RagDependencyException;
import com.spt.learningmanage.model.dto.knowledge.EmbeddingBatchResult;
import com.spt.learningmanage.model.dto.knowledge.EmbeddingCallContext;
import com.spt.learningmanage.model.dto.knowledge.VectorAccessFilter;
import com.spt.learningmanage.model.dto.knowledge.VectorSearchHit;
import com.spt.learningmanage.model.dto.knowledge.VectorSearchRequest;
import com.spt.learningmanage.model.dto.rag.RerankCandidate;
import com.spt.learningmanage.model.dto.rag.RerankItem;
import com.spt.learningmanage.model.dto.rag.RerankRequest;
import com.spt.learningmanage.model.dto.rag.RerankResult;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.model.rag.RagCandidate;
import com.spt.learningmanage.model.rag.RagRetrievalOutcome;
import com.spt.learningmanage.service.EmbeddingClient;
import com.spt.learningmanage.service.RerankClient;
import com.spt.learningmanage.service.VectorSearchClient;
import com.spt.learningmanage.service.knowledge.KnowledgeHashing;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagRetrievalService {
    private final RagProperties properties;
    private final EmbeddingClient embeddingClient;
    private final VectorSearchClient vectorSearchClient;
    private final RerankClient rerankClient;
    private final RagCandidateHydrator hydrator;
    private final KnowledgeHashing hashing;

    public RagRetrievalService(RagProperties properties,
                               EmbeddingClient embeddingClient,
                               VectorSearchClient vectorSearchClient,
                               RerankClient rerankClient,
                               RagCandidateHydrator hydrator,
                               KnowledgeHashing hashing) {
        this.properties = properties;
        this.embeddingClient = embeddingClient;
        this.vectorSearchClient = vectorSearchClient;
        this.rerankClient = rerankClient;
        this.hydrator = hydrator;
        this.hashing = hashing;
    }

    public RagRetrievalOutcome retrieve(Long actorUserId,
                                        ProjectAccessScope scope,
                                        String question,
                                        String traceId) {
        try {
            EmbeddingBatchResult embedding = embeddingClient.embedQuery(question,
                    new EmbeddingCallContext(actorUserId, traceId,
                            List.of(hashing.sha256(question))));
            if (embedding.vectors().size() != 1) {
                throw new BusinessException(ErrorCode.RAG_DEPENDENCY_UNAVAILABLE,
                        "查询向量返回数量异常");
            }
            VectorAccessFilter access = new VectorAccessFilter(
                    scope.projectId(), actorUserId, scope.teamId());
            List<VectorSearchHit> hits = vectorSearchClient.query(new VectorSearchRequest(
                    embedding.vectors().get(0), access, properties.getInitialTopK(),
                    properties.getVectorScoreThreshold()));
            List<RagCandidate> hydrated = hydrator.hydrate(actorUserId, scope, hits);
            List<RagCandidate> beforeRerank = limitPerSource(hydrated,
                    properties.getMaxChunksPerSourceBeforeRerank(), properties.getInitialTopK());
            if (beforeRerank.isEmpty()) {
                return new RagRetrievalOutcome(List.of(), hits.size(), 0,
                        false, null, embedding.actualModel(), null);
            }
            try {
                RerankResult reranked = rerankClient.rerank(new RerankRequest(
                        question,
                        beforeRerank.stream()
                                .map(value -> new RerankCandidate(value.candidateId(), value.text()))
                                .toList(),
                        Math.min(properties.getFinalTopK(), beforeRerank.size()), traceId));
                List<RagCandidate> selected = applyRerank(beforeRerank, reranked.items());
                return new RagRetrievalOutcome(finalLimit(selected), hits.size(), hydrated.size(),
                        false, null, embedding.actualModel(), reranked.actualModel());
            } catch (RagDependencyException exception) {
                if (!properties.isRerankFallbackEnabled()) {
                    throw exception;
                }
                List<RagCandidate> fallback = new ArrayList<>(beforeRerank);
                fallback.sort(Comparator.comparingDouble(RagCandidate::vectorScore).reversed());
                return new RagRetrievalOutcome(finalLimit(fallback), hits.size(), hydrated.size(),
                        true, exception.getSafeMessage(), embedding.actualModel(), null);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (KnowledgeIndexException exception) {
            throw new BusinessException(ErrorCode.RAG_DEPENDENCY_UNAVAILABLE, exception.getSafeMessage());
        } catch (RagDependencyException exception) {
            throw new BusinessException(exception.getErrorCode(), exception.getSafeMessage());
        }
    }

    private List<RagCandidate> applyRerank(List<RagCandidate> candidates, List<RerankItem> items) {
        Map<String, RagCandidate> byId = new HashMap<>();
        candidates.forEach(value -> byId.put(value.candidateId(), value));
        List<RagCandidate> result = new ArrayList<>();
        for (RerankItem item : items) {
            RagCandidate candidate = byId.get(item.candidateId());
            if (candidate != null && item.score() >= properties.getRerankScoreThreshold()) {
                result.add(candidate.withRerankScore(item.score()));
            }
        }
        result.sort(Comparator.comparingDouble(RagCandidate::finalScore).reversed());
        return result;
    }

    private List<RagCandidate> finalLimit(List<RagCandidate> candidates) {
        List<RagCandidate> limited = limitPerSource(candidates,
                properties.getMaxChunksPerSourceAfterRerank(), properties.getFinalTopK());
        List<RagCandidate> result = new ArrayList<>();
        int chars = 0;
        for (RagCandidate candidate : limited) {
            if (chars + candidate.text().length() > properties.getMaxContextChars()) {
                continue;
            }
            result.add(candidate);
            chars += candidate.text().length();
        }
        return List.copyOf(result);
    }

    private List<RagCandidate> limitPerSource(List<RagCandidate> candidates, int perSource, int total) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<RagCandidate> result = new ArrayList<>();
        for (RagCandidate candidate : candidates) {
            String key = candidate.sourceType() + ":" + candidate.sourceId();
            int count = counts.getOrDefault(key, 0);
            if (count >= perSource) {
                continue;
            }
            result.add(candidate);
            counts.put(key, count + 1);
            if (result.size() >= total) {
                break;
            }
        }
        return List.copyOf(result);
    }
}
