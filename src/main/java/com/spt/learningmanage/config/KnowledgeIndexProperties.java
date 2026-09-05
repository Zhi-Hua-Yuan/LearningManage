package com.spt.learningmanage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai.knowledge-index")
public class KnowledgeIndexProperties {

    private boolean workerEnabled = false;
    private int pollIntervalMs = 2000;
    private int claimBatchSize = 20;
    private int workerConcurrency = 4;
    private int leaseSeconds = 60;
    private int maxAttempts = 5;
    private int embeddingMaxConcurrentCalls = 4;
    private int vectorMaxConcurrentCalls = 8;
    private int circuitSlidingWindowSize = 20;
    private int circuitMinimumCalls = 10;
    private float circuitFailureRateThreshold = 50.0f;
    private int circuitOpenWaitMs = 30000;
}
