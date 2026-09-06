package com.spt.learningmanage.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RagConfigurationValidatorTest {
    @Test
    void disabledRagAllowsMissingSecretsButStillValidatesSafeBounds() {
        RagProperties rag = new RagProperties();
        assertDoesNotThrow(() -> validator(rag).validate());
        rag.setInitialTopK(0);
        assertThrows(IllegalStateException.class, () -> validator(rag).validate());
    }

    @Test
    void enabledRagFailsClosedWithoutHmacAndDependencies() {
        RagProperties rag = new RagProperties();
        rag.setEnabled(true);
        assertThrows(IllegalStateException.class, () -> validator(rag).validate());
    }

    @Test
    void enabledRagAcceptsCompleteConfiguration() {
        RagProperties rag = new RagProperties();
        rag.setEnabled(true);
        rag.setQuestionHmacSecret("x".repeat(32));
        RerankProperties rerank = new RerankProperties();
        rerank.setBaseUrl("https://rerank.example");
        rerank.setApiKey("key");
        EmbeddingProperties embedding = new EmbeddingProperties();
        embedding.setQueryBaseUrl("https://embedding.example");
        embedding.setApiKey("key");
        embedding.setModel("text-embedding-v4");
        QdrantProperties qdrant = new QdrantProperties();
        qdrant.setBaseUrl("https://qdrant.example");
        assertDoesNotThrow(() -> new RagConfigurationValidator(
                rag, rerank, embedding, qdrant).validate());
    }

    private RagConfigurationValidator validator(RagProperties rag) {
        return new RagConfigurationValidator(rag, new RerankProperties(),
                new EmbeddingProperties(), new QdrantProperties());
    }
}
