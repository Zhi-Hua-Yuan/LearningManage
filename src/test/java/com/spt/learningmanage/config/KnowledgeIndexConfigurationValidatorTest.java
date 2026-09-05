package com.spt.learningmanage.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeIndexConfigurationValidatorTest {

    @Test
    void disabledWorkerDoesNotRequireExternalConfiguration() {
        KnowledgeIndexProperties index = new KnowledgeIndexProperties();
        EmbeddingProperties embedding = new EmbeddingProperties();
        QdrantProperties qdrant = new QdrantProperties();

        assertDoesNotThrow(() -> new KnowledgeIndexConfigurationValidator(index, embedding, qdrant).validate());
    }

    @Test
    void enabledWorkerRequiresEmbeddingAndQdrantConfiguration() {
        KnowledgeIndexProperties index = new KnowledgeIndexProperties();
        index.setWorkerEnabled(true);

        assertThrows(IllegalStateException.class, () -> new KnowledgeIndexConfigurationValidator(
                index, new EmbeddingProperties(), new QdrantProperties()).validate());
    }

    @Test
    void enabledWorkerAcceptsFrozenStage4Configuration() {
        KnowledgeIndexProperties index = new KnowledgeIndexProperties();
        index.setWorkerEnabled(true);
        EmbeddingProperties embedding = new EmbeddingProperties();
        embedding.setBaseUrl("http://localhost:18080/compatible-mode/v1");
        embedding.setApiKey("test-key");
        QdrantProperties qdrant = new QdrantProperties();
        qdrant.setBaseUrl("http://localhost:16333");

        assertDoesNotThrow(() -> new KnowledgeIndexConfigurationValidator(index, embedding, qdrant).validate());
    }

    @Test
    void invalidDimensionAndUnsafeCollectionNameFail() {
        KnowledgeIndexProperties index = new KnowledgeIndexProperties();
        EmbeddingProperties invalidDimension = new EmbeddingProperties();
        invalidDimension.setDimension(1000);
        assertThrows(IllegalStateException.class, () -> new KnowledgeIndexConfigurationValidator(
                index, invalidDimension, new QdrantProperties()).validate());

        index.setWorkerEnabled(true);
        EmbeddingProperties embedding = new EmbeddingProperties();
        embedding.setBaseUrl("http://localhost");
        embedding.setApiKey("test-key");
        QdrantProperties qdrant = new QdrantProperties();
        qdrant.setBaseUrl("http://localhost");
        qdrant.setCollection("../unsafe");
        assertThrows(IllegalStateException.class, () -> new KnowledgeIndexConfigurationValidator(
                index, embedding, qdrant).validate());
    }

    @Test
    void secureQdrantModeRequiresHttpsAndApiKey() {
        KnowledgeIndexProperties index = new KnowledgeIndexProperties();
        index.setWorkerEnabled(true);
        EmbeddingProperties embedding = new EmbeddingProperties();
        embedding.setBaseUrl("https://embedding.example/v1");
        embedding.setApiKey("embedding-key");
        QdrantProperties qdrant = new QdrantProperties();
        qdrant.setBaseUrl("http://qdrant.internal:6333");
        qdrant.setRequireSecureTransport(true);

        assertThrows(IllegalStateException.class, () -> new KnowledgeIndexConfigurationValidator(
                index, embedding, qdrant).validate());

        qdrant.setBaseUrl("https://qdrant.example");
        qdrant.setApiKey("qdrant-key");
        assertDoesNotThrow(() -> new KnowledgeIndexConfigurationValidator(index, embedding, qdrant).validate());
    }
}
