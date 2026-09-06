package com.spt.learningmanage.job;

import com.spt.learningmanage.config.AgentProperties;
import com.spt.learningmanage.service.agent.AgentRunQueueService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Component
public class AgentRunPollJob {
    private final AgentProperties properties;
    private final AgentRunQueueService queueService;
    private final AgentRunWorker worker;
    private final Executor executor;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName();

    public AgentRunPollJob(AgentProperties properties,
                           AgentRunQueueService queueService,
                           AgentRunWorker worker,
                           @Qualifier("agentRunExecutor") Executor executor) {
        this.properties = properties;
        this.queueService = queueService;
        this.worker = worker;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${ai.agent.poll-delay-ms:1000}")
    public void poll() {
        if (!properties.isEnabled() || !properties.isWorkerEnabled()) {
            return;
        }
        queueService.claimReady(workerId, properties.getBatchSize()).forEach(run -> {
            try {
                executor.execute(() -> worker.process(run));
            } catch (RejectedExecutionException ignored) {
                // Lease expiry makes the durable run claimable again; no in-memory queue growth.
            }
        });
    }
}
