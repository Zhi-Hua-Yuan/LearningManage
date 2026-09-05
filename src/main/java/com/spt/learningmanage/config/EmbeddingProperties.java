package com.spt.learningmanage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai.embedding")
public class EmbeddingProperties {

    private String baseUrl;
    private String apiKey;
    private String model = "text-embedding-v4";
    private int dimension = 1024;
    private int maxBatchSize = 10;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 30000;
}
