package com.spt.learningmanage.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

@Component
public class KnowledgeIndexConfigurationValidator {

    private static final Pattern QDRANT_NAME = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final Set<Integer> V4_DIMENSIONS = Set.of(64, 128, 256, 512, 768, 1024, 1536, 2048);

    private final KnowledgeIndexProperties indexProperties;
    private final EmbeddingProperties embeddingProperties;
    private final QdrantProperties qdrantProperties;

    public KnowledgeIndexConfigurationValidator(KnowledgeIndexProperties indexProperties,
                                                EmbeddingProperties embeddingProperties,
                                                QdrantProperties qdrantProperties) {
        this.indexProperties = indexProperties;
        this.embeddingProperties = embeddingProperties;
        this.qdrantProperties = qdrantProperties;
    }

    @PostConstruct
    public void validate() {
        requireRange("ai.knowledge-index.poll-interval-ms", indexProperties.getPollIntervalMs(), 100, 60000);
        requireRange("ai.knowledge-index.claim-batch-size", indexProperties.getClaimBatchSize(), 1, 500);
        requireRange("ai.knowledge-index.worker-concurrency", indexProperties.getWorkerConcurrency(), 1, 32);
        requireRange("ai.knowledge-index.lease-seconds", indexProperties.getLeaseSeconds(), 10, 3600);
        requireRange("ai.knowledge-index.max-attempts", indexProperties.getMaxAttempts(), 1, 20);
        requireRange("ai.embedding.max-batch-size", embeddingProperties.getMaxBatchSize(), 1, 10);
        requireRange("ai.embedding.connect-timeout-ms", embeddingProperties.getConnectTimeoutMs(), 1000, 30000);
        requireRange("ai.embedding.read-timeout-ms", embeddingProperties.getReadTimeoutMs(), 1000, 300000);
        if (!V4_DIMENSIONS.contains(embeddingProperties.getDimension())) {
            throw new IllegalStateException("ai.embedding.dimension is unsupported by text-embedding-v4");
        }
        if (!indexProperties.isWorkerEnabled()) {
            return;
        }
        requireText("ai.embedding.base-url", embeddingProperties.getBaseUrl());
        requireText("ai.embedding.api-key", embeddingProperties.getApiKey());
        requireText("ai.embedding.model", embeddingProperties.getModel());
        requireText("qdrant.base-url", qdrantProperties.getBaseUrl());
        requireName("qdrant.collection", qdrantProperties.getCollection());
        requireName("qdrant.alias", qdrantProperties.getAlias());
        if (qdrantProperties.getCollection().equals(qdrantProperties.getAlias())) {
            throw new IllegalStateException("qdrant.collection and qdrant.alias must differ");
        }
    }

    private void requireRange(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalStateException(name + " must be between " + min + " and " + max);
        }
    }

    private void requireText(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured when the knowledge worker is enabled");
        }
    }

    private void requireName(String name, String value) {
        if (value == null || !QDRANT_NAME.matcher(value).matches()) {
            throw new IllegalStateException(name + " contains unsupported characters");
        }
    }
}
