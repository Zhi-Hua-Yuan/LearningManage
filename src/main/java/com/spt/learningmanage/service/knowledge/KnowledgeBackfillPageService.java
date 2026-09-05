package com.spt.learningmanage.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.spt.learningmanage.constant.KnowledgeEventTypeEnum;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.config.KnowledgeIndexProperties;
import com.spt.learningmanage.mapper.AiKnowledgeBackfillRunMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.WeeklyReviewMapper;
import com.spt.learningmanage.model.entity.AiKnowledgeBackfillRun;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.model.knowledge.BackfillPageResult;
import com.spt.learningmanage.service.KnowledgeIndexEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class KnowledgeBackfillPageService {

    private final TaskMapper taskMapper;
    private final WeeklyReviewMapper weeklyReviewMapper;
    private final AiKnowledgeBackfillRunMapper runMapper;
    private final KnowledgeIndexEventPublisher publisher;
    private final KnowledgeIndexProperties properties;

    public KnowledgeBackfillPageService(TaskMapper taskMapper,
                                        WeeklyReviewMapper weeklyReviewMapper,
                                        AiKnowledgeBackfillRunMapper runMapper,
                                        KnowledgeIndexEventPublisher publisher,
                                        KnowledgeIndexProperties properties) {
        this.taskMapper = taskMapper;
        this.weeklyReviewMapper = weeklyReviewMapper;
        this.runMapper = runMapper;
        this.publisher = publisher;
        this.properties = properties;
    }

    @Transactional(rollbackFor = Exception.class)
    public BackfillPageResult enqueueTaskPage(AiKnowledgeBackfillRun run) {
        List<Long> ids = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                        .gt(Task::getId, run.getCursorTaskId())
                        .eq(Task::getIsDelete, 0)
                        .orderByAsc(Task::getId)
                        .last("LIMIT " + run.getBatchSize()))
                .stream().map(Task::getId).toList();
        return enqueue(run, ids, KnowledgeSourceTypeEnum.TASK, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public BackfillPageResult enqueueReviewPage(AiKnowledgeBackfillRun run) {
        List<Long> ids = weeklyReviewMapper.selectList(new LambdaQueryWrapper<WeeklyReview>()
                        .gt(WeeklyReview::getId, run.getCursorReviewId())
                        .isNotNull(WeeklyReview::getFocusProjectId)
                        .orderByAsc(WeeklyReview::getId)
                        .last("LIMIT " + run.getBatchSize()))
                .stream().map(WeeklyReview::getId).toList();
        return enqueue(run, ids, KnowledgeSourceTypeEnum.WEEKLY_REVIEW, false);
    }

    private BackfillPageResult enqueue(AiKnowledgeBackfillRun run,
                                       List<Long> ids,
                                       KnowledgeSourceTypeEnum sourceType,
                                       boolean task) {
        for (Long id : ids) {
            publisher.publish(sourceType, id, KnowledgeEventTypeEnum.REBUILD, run.getId());
        }
        long lastId = ids.isEmpty() ? Long.MAX_VALUE : ids.get(ids.size() - 1);
        long discovered = run.getDiscoveredCount() + ids.size();
        long enqueued = run.getEnqueuedCount() + ids.size();
        UpdateWrapper<AiKnowledgeBackfillRun> update = new UpdateWrapper<AiKnowledgeBackfillRun>()
                .eq("id", run.getId())
                .eq("claim_token", run.getClaimToken())
                .set(task ? "cursor_task_id" : "cursor_review_id",
                        ids.size() < run.getBatchSize() ? Long.MAX_VALUE : lastId)
                .set("discovered_count", discovered)
                .set("enqueued_count", enqueued)
                .set("lease_until", java.time.LocalDateTime.now().plusSeconds(properties.getLeaseSeconds()));
        if (runMapper.update(null, update) != 1) {
            throw new IllegalStateException("Knowledge backfill page lost fencing token");
        }
        run.setDiscoveredCount(discovered);
        run.setEnqueuedCount(enqueued);
        if (task) {
            run.setCursorTaskId(ids.size() < run.getBatchSize() ? Long.MAX_VALUE : lastId);
        } else {
            run.setCursorReviewId(ids.size() < run.getBatchSize() ? Long.MAX_VALUE : lastId);
        }
        return new BackfillPageResult(ids.size(), lastId, ids.size() < run.getBatchSize());
    }
}
