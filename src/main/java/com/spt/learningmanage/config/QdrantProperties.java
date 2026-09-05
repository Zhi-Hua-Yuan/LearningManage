package com.spt.learningmanage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "qdrant")
public class QdrantProperties {

    private String baseUrl;
    private String apiKey;
    private String collection = "learning_knowledge_v1_1024";
    private String alias = "learning_knowledge_current";
}
