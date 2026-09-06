package com.spt.learningmanage.service.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.config.RagProperties;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.exception.RagDependencyException;
import com.spt.learningmanage.model.dto.knowledge.EmbeddingBatchResult;
import com.spt.learningmanage.model.dto.knowledge.VectorSearchHit;
import com.spt.learningmanage.model.dto.rag.RerankItem;
import com.spt.learningmanage.model.dto.rag.RerankResult;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.model.rag.RagCandidate;
import com.spt.learningmanage.service.EmbeddingClient;
import com.spt.learningmanage.service.RerankClient;
import com.spt.learningmanage.service.VectorStoreClient;
import com.spt.learningmanage.service.knowledge.KnowledgeHashing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagRetrievalServiceTest {
    private final EmbeddingClient embedding = mock(EmbeddingClient.class);
    private final VectorStoreClient vectors = mock(VectorStoreClient.class);
    private final RerankClient rerank = mock(RerankClient.class);
    private final RagCandidateHydrator hydrator = mock(RagCandidateHydrator.class);
    private RagProperties properties;
    private RagRetrievalService service;

    @BeforeEach
    void setUp() {
        properties = new RagProperties();
        service = new RagRetrievalService(properties, embedding, vectors, rerank, hydrator,
                new KnowledgeHashing(new ObjectMapper()));
        when(embedding.embedQuery(any(), any())).thenReturn(new EmbeddingBatchResult(
                List.of(List.of(0.1f, 0.2f)), "text-embedding-v4", 2L, 2L, "e1"));
    }

    @Test
    void emptyAuthorizedSetSkipsRerankAndChatPreparation() {
        List<VectorSearchHit> hits = List.of(new VectorSearchHit("p1", 0.9, Map.of()));
        when(vectors.query(any())).thenReturn(hits);
        when(hydrator.hydrate(any(), any(), any())).thenReturn(List.of());

        var result = service.retrieve(7L, scope(), "问题", "trace");

        assertEquals(1, result.vectorCandidateCount());
        assertEquals(0, result.authorizedCandidateCount());
        assertEquals(List.of(), result.candidates());
        verify(rerank, never()).rerank(any());
    }

    @Test
    void rerankFailureFallsBackToVectorOrderAndMarksDegradation() {
        RagCandidate lower = candidate("a", 1L, 0.6);
        RagCandidate higher = candidate("b", 2L, 0.9);
        when(vectors.query(any())).thenReturn(List.of());
        when(hydrator.hydrate(any(), any(), any())).thenReturn(List.of(lower, higher));
        when(rerank.rerank(any())).thenThrow(new RagDependencyException(
                ErrorCode.RERANK_UNAVAILABLE, true, "重排不可用", "down", null));

        var result = service.retrieve(7L, scope(), "问题", "trace");

        assertTrue(result.degraded());
        assertEquals(List.of("b", "a"), result.candidates().stream()
                .map(RagCandidate::candidateId).toList());
    }

    @Test
    void rerankThresholdAndProviderOrderControlFinalCandidates() {
        RagCandidate first = candidate("a", 1L, 0.9);
        RagCandidate second = candidate("b", 2L, 0.8);
        when(vectors.query(any())).thenReturn(List.of());
        when(hydrator.hydrate(any(), any(), any())).thenReturn(List.of(first, second));
        when(rerank.rerank(any())).thenReturn(new RerankResult(List.of(
                new RerankItem("b", 1, 0.95),
                new RerankItem("a", 0, 0.10)), "qwen3-rerank", 10L, "r1"));

        var result = service.retrieve(7L, scope(), "问题", "trace");

        assertEquals(List.of("b"), result.candidates().stream()
                .map(RagCandidate::candidateId).toList());
        assertEquals(0.95, result.candidates().get(0).rerankScore());
    }

    private ProjectAccessScope scope() {
        return new ProjectAccessScope(7L, 10L, 7L, null, null);
    }

    private RagCandidate candidate(String id, Long sourceId, double vectorScore) {
        return new RagCandidate(id, id, "TASK:" + sourceId,
                KnowledgeSourceTypeEnum.TASK, sourceId, 0, "title", "body",
                "a".repeat(64), "b".repeat(64), vectorScore, null, null);
    }
}
