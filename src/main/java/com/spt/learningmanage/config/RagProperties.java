package com.spt.learningmanage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai.rag")
public class RagProperties {

    private boolean enabled = false;
    private int initialTopK = 20;
    private int finalTopK = 8;
    private int maxChunksPerSourceBeforeRerank = 3;
    private int maxChunksPerSourceAfterRerank = 2;
    private double vectorScoreThreshold = 0.25d;
    private double rerankScoreThreshold = 0.20d;
    private int maxQuestionChars = 1000;
    private int maxContextChars = 12000;
    private int resultRetentionDays = 30;
    private String retrievalConfigVersion = "rag-v1";
    private boolean requireCompletedBackfill = true;
    private boolean rerankFallbackEnabled = true;
    private String questionHmacSecret = "";
    private int statusRefreshMs = 600000;
    private int statusRefreshBatchSize = 100;
}
