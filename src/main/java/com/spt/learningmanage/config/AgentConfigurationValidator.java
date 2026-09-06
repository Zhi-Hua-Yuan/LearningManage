package com.spt.learningmanage.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class AgentConfigurationValidator {
    private final AgentProperties properties;

    public AgentConfigurationValidator(AgentProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void validate() {
        range("ai.agent.poll-delay-ms", properties.getPollDelayMs(), 100, 60000);
        range("ai.agent.batch-size", properties.getBatchSize(), 1, 100);
        range("ai.agent.max-concurrent-runs", properties.getMaxConcurrentRuns(), 1, 64);
        range("ai.agent.max-concurrent-runs-per-user", properties.getMaxConcurrentRunsPerUser(), 1, 10);
        range("ai.agent.lease-seconds", properties.getLeaseSeconds(), 10, 3600);
        range("ai.agent.heartbeat-seconds", properties.getHeartbeatSeconds(), 1, properties.getLeaseSeconds() - 1);
        range("ai.agent.overall-timeout-seconds", properties.getOverallTimeoutSeconds(), 10, 600);
        range("ai.agent.tool-timeout-seconds", properties.getToolTimeoutSeconds(), 1,
                properties.getOverallTimeoutSeconds());
        range("ai.agent.max-tool-calls", properties.getMaxToolCalls(), 1, 4);
        range("ai.agent.max-attempts", properties.getMaxAttempts(), 1, 2);
        range("ai.agent.max-tool-output-chars", properties.getMaxToolOutputChars(), 1000, 50000);
        range("ai.agent.project-task-limit", properties.getProjectTaskLimit(), 1, 50);
        range("ai.agent.history-limit", properties.getHistoryLimit(), 1, 8);
        range("ai.agent.draft-ttl-minutes", properties.getDraftTtlMinutes(), 5, 1440);
        if (properties.isWorkerEnabled() && !properties.isEnabled()) {
            throw new IllegalStateException("ai.agent.worker-enabled requires ai.agent.enabled=true");
        }
        if (properties.isToolCallingEnabled() && !properties.isEnabled()) {
            throw new IllegalStateException("ai.agent.tool-calling-enabled requires ai.agent.enabled=true");
        }
    }

    private void range(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalStateException(name + " must be between " + min + " and " + max);
        }
    }
}
