package com.spt.learningmanage.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/** Validates the Embedding transport selector before client beans are wired. */
@Component
public class EmbeddingAdapterConfigurationValidator {

    private final EmbeddingProperties properties;

    public EmbeddingAdapterConfigurationValidator(EmbeddingProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void validate() {
        String adapter = properties.getAdapter();
        if (!"legacy".equals(adapter) && !"spring-ai".equals(adapter)) {
            throw new IllegalStateException(
                    "ai.embedding.adapter must be one of [legacy, spring-ai], but was: " + adapter);
        }
    }
}
