package com.spt.learningmanage.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.spt.learningmanage.constant.KnowledgeBackfillStatusEnum;
import com.spt.learningmanage.mapper.AiKnowledgeBackfillRunMapper;
import com.spt.learningmanage.model.entity.AiKnowledgeBackfillRun;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class KnowledgeBackfillQueueService {

    private final AiKnowledgeBackfillRunMapper mapper;

    public KnowledgeBackfillQueueService(AiKnowledgeBackfillRunMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public AiKnowledgeBackfillRun claim(String workerId, int leaseSeconds) {
        LocalDateTime now = LocalDateTime.now();
        AiKnowledgeBackfillRun run = mapper.selectReadyForUpdate(now);
        if (run == null) {
            return null;
        }
        run.setStatus(KnowledgeBackfillStatusEnum.RUNNING.name());
        run.setWorkerId(workerId);
        run.setClaimToken(UUID.randomUUID().toString());
        run.setLeaseUntil(now.plusSeconds(leaseSeconds));
        if (run.getStartedAt() == null) {
            run.setStartedAt(now);
        }
        if (mapper.updateById(run) != 1) {
            throw new IllegalStateException("Unable to claim knowledge backfill " + run.getId());
        }
        return run;
    }

    @Transactional(rollbackFor = Exception.class)
    public void markEnqueued(AiKnowledgeBackfillRun run) {
        int rows = mapper.update(null, fenced(run)
                .set("status", KnowledgeBackfillStatusEnum.ENQUEUED.name())
                .set("lease_until", null)
                .set("worker_id", null));
        if (rows != 1) {
            throw new IllegalStateException("Knowledge backfill fencing token was lost");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void fail(AiKnowledgeBackfillRun run) {
        mapper.update(null, fenced(run)
                .set("status", KnowledgeBackfillStatusEnum.FAILED.name())
                .set("lease_until", null)
                .set("finished_at", LocalDateTime.now()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateCompletion(AiKnowledgeBackfillRun run,
                                 long successCount,
                                 long failedCount,
                                 long deadCount,
                                 boolean terminal) {
        UpdateWrapper<AiKnowledgeBackfillRun> update = new UpdateWrapper<AiKnowledgeBackfillRun>()
                .eq("id", run.getId())
                .eq("status", KnowledgeBackfillStatusEnum.ENQUEUED.name())
                .set("success_count", successCount)
                .set("failed_count", failedCount)
                .set("dead_count", deadCount);
        if (terminal) {
            update.set("status", deadCount == 0
                            ? KnowledgeBackfillStatusEnum.SUCCEEDED.name()
                            : KnowledgeBackfillStatusEnum.PARTIAL.name())
                    .set("finished_at", LocalDateTime.now());
        }
        mapper.update(null, update);
    }

    private UpdateWrapper<AiKnowledgeBackfillRun> fenced(AiKnowledgeBackfillRun run) {
        return new UpdateWrapper<AiKnowledgeBackfillRun>()
                .eq("id", run.getId())
                .eq("status", KnowledgeBackfillStatusEnum.RUNNING.name())
                .eq("claim_token", run.getClaimToken());
    }
}
