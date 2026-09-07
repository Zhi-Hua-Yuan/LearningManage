package com.spt.learningmanage.job;

import com.spt.learningmanage.config.DataCleanupProperties;
import com.spt.learningmanage.model.entity.AiDataCleanupRun;
import com.spt.learningmanage.service.CleanupRunQueueService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.management.ManagementFactory;
import java.util.concurrent.Executor;

@Component
public class DataCleanupPollJob {
    private final DataCleanupProperties properties;
    private final CleanupRunQueueService queueService;
    private final DataCleanupWorker worker;
    private final Executor executor;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName();

    public DataCleanupPollJob(DataCleanupProperties properties,
                              CleanupRunQueueService queueService,
                              DataCleanupWorker worker,
                              @Qualifier("dataCleanupExecutor") Executor executor) {
        this.properties = properties;
        this.queueService = queueService;
        this.worker = worker;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${ai.cleanup.poll-delay-ms:5000}")
    public void poll() {
        if (!properties.isEnabled()) {
            return;
        }
        AiDataCleanupRun run = queueService.claimOne(workerId);
        if (run != null) {
            try {
                executor.execute(() -> worker.process(run));
            } catch (java.util.concurrent.RejectedExecutionException exception) {
                queueService.releaseForResume(run);
            }
        }
    }
}
