package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.WeeklyReviewTaskMapper;
import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.model.entity.WeeklyReviewTask;
import com.spt.learningmanage.model.review.WeeklyReviewReadableAssociations;
import com.spt.learningmanage.service.PermissionBatchPolicy;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.WeeklyReviewReadAssociationResolver;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Batch-first read authorization for persisted weekly-review associations.
 * A lost resource permission is filtered from the response; it is not a
 * reason to deny the author's otherwise valid review view.
 */
@Service
public class WeeklyReviewReadAssociationResolverImpl implements WeeklyReviewReadAssociationResolver {

    @Resource
    private WeeklyReviewTaskMapper weeklyReviewTaskMapper;

    @Resource
    private PermissionService permissionService;

    @Override
    public WeeklyReviewReadableAssociations resolve(Long actorUserId, List<WeeklyReview> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return WeeklyReviewReadableAssociations.empty();
        }

        Set<Long> reviewIds = new LinkedHashSet<>();
        Set<Long> focusProjectIds = new LinkedHashSet<>();
        for (WeeklyReview review : reviews) {
            if (review == null || review.getId() == null || review.getId() <= 0) {
                continue;
            }
            reviewIds.add(review.getId());
            if (review.getFocusProjectId() != null) {
                if (review.getFocusProjectId() <= 0) {
                    throw corruptedAssociations();
                }
                focusProjectIds.add(review.getFocusProjectId());
            }
        }

        Map<Long, List<Long>> persistedTaskIds = loadTaskIds(reviewIds);
        Set<Long> readableTaskIds = filterReadableTasks(actorUserId, persistedTaskIds.values());
        Map<Long, List<Long>> readableByReview = new LinkedHashMap<>();
        for (Long reviewId : reviewIds) {
            List<Long> readable = persistedTaskIds.getOrDefault(reviewId, List.of()).stream()
                    .filter(readableTaskIds::contains)
                    .toList();
            readableByReview.put(reviewId, readable);
        }

        Set<Long> readableProjects = resolveReadableProjects(actorUserId, focusProjectIds);
        return new WeeklyReviewReadableAssociations(readableByReview, readableProjects);
    }

    private Map<Long, List<Long>> loadTaskIds(Collection<Long> reviewIds) {
        if (reviewIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<WeeklyReviewTask> relations = weeklyReviewTaskMapper.selectByReviewIds(reviewIds);
        Set<Long> expectedReviewIds = new LinkedHashSet<>(reviewIds);
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        Set<String> uniquePairs = new LinkedHashSet<>();
        if (relations == null) {
            return result;
        }
        for (WeeklyReviewTask relation : relations) {
            if (relation == null || relation.getWeeklyReviewId() == null
                    || relation.getWeeklyReviewId() <= 0 || relation.getTaskId() == null
                    || relation.getTaskId() <= 0
                    || !expectedReviewIds.contains(relation.getWeeklyReviewId())) {
                throw corruptedAssociations();
            }
            String pair = relation.getWeeklyReviewId() + ":" + relation.getTaskId();
            if (!uniquePairs.add(pair)) {
                throw corruptedAssociations();
            }
            result.computeIfAbsent(relation.getWeeklyReviewId(), ignored -> new ArrayList<>())
                    .add(relation.getTaskId());
        }
        return result;
    }

    private Set<Long> filterReadableTasks(Long actorUserId, Collection<List<Long>> taskIdGroups) {
        LinkedHashSet<Long> allTaskIds = new LinkedHashSet<>();
        for (List<Long> group : taskIdGroups) {
            allTaskIds.addAll(group);
        }
        LinkedHashSet<Long> readable = new LinkedHashSet<>();
        for (List<Long> batch : partition(allTaskIds)) {
            Set<Long> batchReadable = permissionService.filterReadableTaskIds(actorUserId, batch);
            if (batchReadable != null) {
                readable.addAll(batchReadable);
            }
        }
        return readable;
    }

    private Set<Long> resolveReadableProjects(Long actorUserId, Collection<Long> projectIds) {
        LinkedHashSet<Long> readable = new LinkedHashSet<>();
        for (List<Long> batch : partition(projectIds)) {
            Map<Long, ?> scopes = permissionService.resolveProjectScopes(actorUserId, batch);
            if (scopes != null) {
                readable.addAll(scopes.keySet());
            }
        }
        return readable;
    }

    private <T> List<List<T>> partition(Collection<T> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<T> all = new ArrayList<>(values);
        List<List<T>> batches = new ArrayList<>();
        for (int from = 0; from < all.size(); from += PermissionBatchPolicy.MAX_RESOURCE_IDS) {
            batches.add(all.subList(from,
                    Math.min(from + PermissionBatchPolicy.MAX_RESOURCE_IDS, all.size())));
        }
        return batches;
    }

    private BusinessException corruptedAssociations() {
        return new BusinessException(ErrorCode.SYSTEM_ERROR, "周总结关联数据异常");
    }
}
