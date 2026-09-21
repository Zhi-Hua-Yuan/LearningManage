package com.spt.learningmanage.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.ai.governance.AiContentSanitizer;
import com.spt.learningmanage.client.knowledge.SpringAiEmbeddingModel;
import com.spt.learningmanage.service.EmbeddingClient;
import com.spt.learningmanage.service.impl.SpringAiEmbeddingClient;
import com.spt.learningmanage.service.knowledge.KnowledgeResilientCallExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ai.embedding.adapter", havingValue = "spring-ai")
public class SpringAiEmbeddingConfiguration {

    @Bean("documentEmbeddingModel")
    public SpringAiEmbeddingModel documentEmbeddingModel(EmbeddingProperties properties,
                                                          ObjectMapper objectMapper) {
        return new SpringAiEmbeddingModel(properties, objectMapper, SpringAiEmbeddingModel.Mode.DOCUMENT);
    }

    @Bean("queryEmbeddingModel")
    public SpringAiEmbeddingModel queryEmbeddingModel(EmbeddingProperties properties,
                                                       ObjectMapper objectMapper) {
        return new SpringAiEmbeddingModel(properties, objectMapper, SpringAiEmbeddingModel.Mode.QUERY);
    }

    @Bean
    public EmbeddingClient springAiEmbeddingClient(EmbeddingProperties properties,
                                                   ObjectMapper objectMapper,
                                                   @Qualifier("documentEmbeddingModel") SpringAiEmbeddingModel documentEmbeddingModel,
                                                   @Qualifier("queryEmbeddingModel") SpringAiEmbeddingModel queryEmbeddingModel,
                                                   AiContentSanitizer contentSanitizer,
                                                   KnowledgeResilientCallExecutor resilientCallExecutor) {
        return new SpringAiEmbeddingClient(properties, objectMapper, documentEmbeddingModel,
                queryEmbeddingModel, contentSanitizer, resilientCallExecutor);
    }
}
