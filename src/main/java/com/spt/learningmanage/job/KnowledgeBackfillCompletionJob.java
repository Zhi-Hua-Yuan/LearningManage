package com.spt.learningmanage.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spt.learningmanage.config.KnowledgeIndexProperties;
import com.spt.learningmanage.constant.KnowledgeBackfillStatusEnum;
import com.spt.learningmanage.constant.KnowledgeEventStatusEnum;
import com.spt.learningmanage.mapper.AiKnowledgeBackfillRunMapper;
import com.spt.learningmanage.mapper.AiKnowledgeIndexEventMapper;
import com.spt.learningmanage.model.entity.AiKnowledgeBackfillRun;
import com.spt.learningmanage.model.entity.AiKnowledgeIndexEvent;
import com.spt.learningmanage.service.knowledge.KnowledgeBackfillQueueService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeBackfillCompletionJob {

    private final KnowledgeIndexProperties properties;
    private final AiKnowledgeBackfillRunMapper runMapper;
    private final AiKnowledgeIndexEventMapper eventMapper;
    private final KnowledgeBackfillQueueService queueService;

    public KnowledgeBackfillCompletionJob(KnowledgeIndexProperties properties,
                                          AiKnowledgeBackfillRunMapper runMapper,
                                          AiKnowledgeIndexEventMapper eventMapper,
                                          KnowledgeBackfillQueueService queueService) {
        this.properties = properties;
        this.runMapper = runMapper;
        this.eventMapper = eventMapper;
        this.queueService = queueService;
    }

    @Scheduled(fixedDelayString = "${ai.knowledge-index.backfill-monitor-interval-ms:5000}")
    public void monitor() {
        if (!properties.isWorkerEnabled()) {
            return;
        }
        for (AiKnowledgeBackfillRun run : runMapper.selectList(
                new LambdaQueryWrapper<AiKnowledgeBackfillRun>()
                        .eq(AiKnowledgeBackfillRun::getStatus, KnowledgeBackfillStatusEnum.ENQUEUED.name())
                        .orderByAsc(AiKnowledgeBackfillRun::getId)
                        .last("LIMIT 20"))) {
            long success = count(run.getId(), KnowledgeEventStatusEnum.SUCCESS);
            long dead = count(run.getId(), KnowledgeEventStatusEnum.DEAD);
            long failed = eventMapper.selectCount(new LambdaQueryWrapper<AiKnowledgeIndexEvent>()
                    .eq(AiKnowledgeIndexEvent::getBackfillRunId, run.getId())
                    .gt(AiKnowledgeIndexEvent::getAttemptCount, 0));
            boolean terminal = success + dead >= run.getEnqueuedCount();
            queueService.updateCompletion(run, success, failed, dead, terminal);
        }
    }

    private long count(Long runId, KnowledgeEventStatusEnum status) {
        return eventMapper.selectCount(new LambdaQueryWrapper<AiKnowledgeIndexEvent>()
                .eq(AiKnowledgeIndexEvent::getBackfillRunId, runId)
                .eq(AiKnowledgeIndexEvent::getStatus, status.name()));
    }
}
