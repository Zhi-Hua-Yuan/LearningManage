package com.spt.learningmanage.job;

import com.spt.learningmanage.config.DataCleanupProperties;
import com.spt.learningmanage.model.entity.AiDataCleanupRun;
import com.spt.learningmanage.service.CleanupRunQueueService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;

@Component
public class DataCleanupPollJob {
    private final DataCleanupProperties properties;
    private final CleanupRunQueueService queueService;
    private final DataCleanupWorker worker;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName();

    public DataCleanupPollJob(DataCleanupProperties properties,
                              CleanupRunQueueService queueService,
                              DataCleanupWorker worker) {
        this.properties = properties;
        this.queueService = queueService;
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${ai.cleanup.poll-delay-ms:5000}")
    public void poll() {
        if (!properties.isEnabled()) {
            return;
        }
        AiDataCleanupRun run = queueService.claimOne(workerId);
        if (run != null) {
            worker.process(run);
        }
    }
}
