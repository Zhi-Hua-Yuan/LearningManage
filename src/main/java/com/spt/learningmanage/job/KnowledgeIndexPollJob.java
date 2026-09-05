package com.spt.learningmanage.job;

import com.spt.learningmanage.config.KnowledgeIndexProperties;
import com.spt.learningmanage.service.knowledge.KnowledgeEventQueueService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.concurrent.Executor;

@Component
public class KnowledgeIndexPollJob {

    private final KnowledgeIndexProperties properties;
    private final KnowledgeEventQueueService queueService;
    private final KnowledgeIndexWorker worker;
    private final Executor executor;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName();

    public KnowledgeIndexPollJob(KnowledgeIndexProperties properties,
                                 KnowledgeEventQueueService queueService,
                                 KnowledgeIndexWorker worker,
                                 @Qualifier("knowledgeIndexExecutor") Executor executor) {
        this.properties = properties;
        this.queueService = queueService;
        this.worker = worker;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${ai.knowledge-index.poll-interval-ms:2000}")
    public void poll() {
        if (!properties.isWorkerEnabled()) {
            return;
        }
        queueService.claimReady(workerId, properties.getClaimBatchSize())
                .forEach(event -> executor.execute(() -> worker.process(event)));
    }
}
