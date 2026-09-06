package com.spt.learningmanage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai.agent")
public class AgentProperties {
    private boolean enabled = false;
    private boolean workerEnabled = false;
    private boolean toolCallingEnabled = false;
    private int pollDelayMs = 1000;
    private int batchSize = 5;
    private int maxConcurrentRuns = 4;
    private int maxConcurrentRunsPerUser = 2;
    private int leaseSeconds = 90;
    private int heartbeatSeconds = 15;
    private int overallTimeoutSeconds = 60;
    private int toolTimeoutSeconds = 10;
    private int maxToolCalls = 4;
    private int maxAttempts = 2;
    private int maxToolOutputChars = 20000;
    private int projectTaskLimit = 50;
    private int historyLimit = 5;
    private int draftTtlMinutes = 30;
}
