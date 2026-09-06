package com.spt.learningmanage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai.rerank")
public class RerankProperties {

    private String baseUrl = "";
    private String apiKey = "";
    private String model = "qwen3-rerank";
    private String instruction = "Given a project-management question, retrieve passages that directly support the answer.";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 30000;
    private int maxConcurrentCalls = 4;
}
