package com.spt.learningmanage.job;

import com.spt.learningmanage.config.KnowledgeIndexProperties;
import com.spt.learningmanage.model.entity.AiKnowledgeBackfillRun;
import com.spt.learningmanage.service.knowledge.KnowledgeBackfillPageService;
import com.spt.learningmanage.service.knowledge.KnowledgeBackfillQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;

@Component
public class KnowledgeBackfillJob {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBackfillJob.class);

    private final KnowledgeIndexProperties properties;
    private final KnowledgeBackfillQueueService queueService;
    private final KnowledgeBackfillPageService pageService;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName() + "-backfill";

    public KnowledgeBackfillJob(KnowledgeIndexProperties properties,
                                KnowledgeBackfillQueueService queueService,
                                KnowledgeBackfillPageService pageService) {
        this.properties = properties;
        this.queueService = queueService;
        this.pageService = pageService;
    }

    @Scheduled(fixedDelayString = "${ai.knowledge-index.backfill-poll-interval-ms:5000}")
    public void run() {
        if (!properties.isWorkerEnabled()) {
            return;
        }
        AiKnowledgeBackfillRun backfill = queueService.claim(workerId, properties.getLeaseSeconds());
        if (backfill == null) {
            return;
        }
        try {
            process(backfill);
            queueService.markEnqueued(backfill);
        } catch (RuntimeException exception) {
            log.warn("knowledge backfill failed: runId={}, type={}",
                    backfill.getId(), exception.getClass().getSimpleName());
            queueService.fail(backfill);
        }
    }

    private void process(AiKnowledgeBackfillRun run) {
        if (("TASK".equals(run.getSourceScope()) || "ALL".equals(run.getSourceScope()))
                && run.getCursorTaskId() != Long.MAX_VALUE) {
            while (!pageService.enqueueTaskPage(run).done()) {
                // keyset pages are individually transactional
            }
        }
        if (("WEEKLY_REVIEW".equals(run.getSourceScope()) || "ALL".equals(run.getSourceScope()))
                && run.getCursorReviewId() != Long.MAX_VALUE) {
            while (!pageService.enqueueReviewPage(run).done()) {
                // keyset pages are individually transactional
            }
        }
    }
}
