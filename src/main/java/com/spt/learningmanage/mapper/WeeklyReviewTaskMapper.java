package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.WeeklyReviewTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * Persistence primitives for the weekly-review/task association table.
 *
 * <p>This mapper deliberately contains no authorization or review lifecycle
 * policy. Those decisions remain in the service layer and are activated by a
 * later C2/C3 work packages.</p>
 */
@Mapper
public interface WeeklyReviewTaskMapper extends BaseMapper<WeeklyReviewTask> {

    /**
     * Load associations for multiple reviews in deterministic review/id order.
     * An empty collection is supported and returns an empty list.
     */
    List<WeeklyReviewTask> selectByReviewIds(
            @Param("reviewIds") Collection<Long> reviewIds);

    /** Delete only associations belonging to one review. */
    int deleteByReviewId(@Param("weeklyReviewId") Long weeklyReviewId);

    /**
     * Insert a non-empty batch. The unique key on (weekly_review_id, task_id)
     * is intentionally enforced by MySQL; callers must handle duplicate-key
     * failures as a transaction boundary concern.
     */
    int batchInsert(@Param("relations") Collection<WeeklyReviewTask> relations);
}
