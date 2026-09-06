package com.spt.learningmanage.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class RagConfigurationValidator {
    private final RagProperties rag;
    private final RerankProperties rerank;
    private final EmbeddingProperties embedding;
    private final QdrantProperties qdrant;

    public RagConfigurationValidator(RagProperties rag,
                                     RerankProperties rerank,
                                     EmbeddingProperties embedding,
                                     QdrantProperties qdrant) {
        this.rag = rag;
        this.rerank = rerank;
        this.embedding = embedding;
        this.qdrant = qdrant;
    }

    @PostConstruct
    public void validate() {
        range("ai.rag.initial-top-k", rag.getInitialTopK(), 1, 100);
        range("ai.rag.final-top-k", rag.getFinalTopK(), 1, rag.getInitialTopK());
        range("ai.rag.max-chunks-per-source-before-rerank",
                rag.getMaxChunksPerSourceBeforeRerank(), 1, rag.getInitialTopK());
        range("ai.rag.max-chunks-per-source-after-rerank",
                rag.getMaxChunksPerSourceAfterRerank(), 1, rag.getFinalTopK());
        decimalRange("ai.rag.vector-score-threshold", rag.getVectorScoreThreshold(), -1.0d, 1.0d);
        decimalRange("ai.rag.rerank-score-threshold", rag.getRerankScoreThreshold(), 0.0d, 1.0d);
        range("ai.rag.max-question-chars", rag.getMaxQuestionChars(), 1, 10000);
        range("ai.rag.max-context-chars", rag.getMaxContextChars(), 1000, 100000);
        range("ai.rag.result-retention-days", rag.getResultRetentionDays(), 1, 365);
        range("ai.rag.status-refresh-ms", rag.getStatusRefreshMs(), 10000, 86400000);
        range("ai.rag.status-refresh-batch-size", rag.getStatusRefreshBatchSize(), 1, 1000);
        range("ai.rerank.max-concurrent-calls", rerank.getMaxConcurrentCalls(), 1, 64);
        range("ai.rerank.connect-timeout-ms", rerank.getConnectTimeoutMs(), 1000, 30000);
        range("ai.rerank.read-timeout-ms", rerank.getReadTimeoutMs(), 1000, 300000);
        if (!rag.isEnabled()) {
            return;
        }
        text("ai.rag.retrieval-config-version", rag.getRetrievalConfigVersion());
        if (rag.getQuestionHmacSecret() == null || rag.getQuestionHmacSecret().length() < 32) {
            throw new IllegalStateException("ai.rag.question-hmac-secret must contain at least 32 characters");
        }
        text("ai.embedding.query-base-url", embedding.getQueryBaseUrl());
        text("ai.embedding.api-key", embedding.getApiKey());
        text("ai.embedding.model", embedding.getModel());
        text("qdrant.base-url", qdrant.getBaseUrl());
        text("ai.rerank.base-url", rerank.getBaseUrl());
        text("ai.rerank.api-key", rerank.getApiKey());
        text("ai.rerank.model", rerank.getModel());
    }

    private void range(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalStateException(name + " must be between " + min + " and " + max);
        }
    }

    private void decimalRange(String name, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalStateException(name + " must be between " + min + " and " + max);
        }
    }

    private void text(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured when RAG is enabled");
        }
    }
}
