package com.spt.learningmanage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai.cleanup")
public class DataCleanupProperties {
    private boolean enabled = false;
    private boolean scheduleEnabled = false;
    private String policyVersion = "stage7-v1";
    private int batchSize = 500;
    private int maxRuntimeSeconds = 600;
    private int leaseSeconds = 90;
    private int pollDelayMs = 5000;
    private int dryRunValidHours = 24;
    private double estimateDriftRatio = 0.10D;
    private long estimateDriftMinRows = 100L;
    private int bodyRetentionDays = 30;
    private int metadataRetentionDays = 90;
    private int successfulEventRetentionDays = 14;
    private int adminAuditRetentionDays = 180;
}
