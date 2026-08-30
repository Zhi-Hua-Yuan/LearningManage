package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.mapper.WeeklyReviewTaskMapper;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.model.entity.WeeklyReviewTask;
import com.spt.learningmanage.model.review.WeeklyReviewReadableAssociations;
import com.spt.learningmanage.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyReviewReadAssociationResolverTest {

    @Mock
    private WeeklyReviewTaskMapper weeklyReviewTaskMapper;

    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private WeeklyReviewReadAssociationResolverImpl resolver;

    @Test
    void resolve_shouldFilterLostTaskAndFocusProjectWithoutDroppingReview() {
        WeeklyReview review = review(7001L, 901L);
        when(weeklyReviewTaskMapper.selectByReviewIds(any())).thenReturn(List.of(
                relation(7001L, 101L), relation(7001L, 102L), relation(7001L, 103L)));
        when(permissionService.filterReadableTaskIds(7L, List.of(101L, 102L, 103L)))
                .thenReturn(Set.of(103L, 101L));
        when(permissionService.resolveProjectScopes(7L, List.of(901L)))
                .thenReturn(Map.of());

        WeeklyReviewReadableAssociations result = resolver.resolve(7L, List.of(review));

        assertEquals(List.of(101L, 103L), result.taskIdsFor(7001L));
        assertEquals(Set.of(), result.readableFocusProjectIds());
        verify(permissionService).filterReadableTaskIds(7L, List.of(101L, 102L, 103L));
        verify(permissionService).resolveProjectScopes(7L, List.of(901L));
    }

    @Test
    void resolve_shouldBatchAllReviewsOnceAndPreserveEachReviewOrder() {
        WeeklyReview first = review(7001L, null);
        WeeklyReview second = review(7002L, null);
        when(weeklyReviewTaskMapper.selectByReviewIds(any())).thenReturn(List.of(
                relation(7001L, 101L), relation(7002L, 201L), relation(7001L, 102L)));
        when(permissionService.filterReadableTaskIds(eq(7L), any()))
                .thenAnswer(invocation -> new LinkedHashSet<>(invocation.getArgument(1)));

        WeeklyReviewReadableAssociations result = resolver.resolve(7L, List.of(first, second));

        assertEquals(List.of(101L, 102L), result.taskIdsFor(7001L));
        assertEquals(List.of(201L), result.taskIdsFor(7002L));
        verify(weeklyReviewTaskMapper, times(1)).selectByReviewIds(any());
        verify(permissionService, times(1)).filterReadableTaskIds(eq(7L), any());
    }

    @Test
    void resolve_shouldSplitTaskPermissionBatchesAtSharedLimit() {
        WeeklyReview review = review(7001L, null);
        List<WeeklyReviewTask> relations = new ArrayList<>();
        for (long taskId = 1; taskId <= 501; taskId++) {
            relations.add(relation(7001L, taskId));
        }
        when(weeklyReviewTaskMapper.selectByReviewIds(any())).thenReturn(relations);
        when(permissionService.filterReadableTaskIds(eq(7L), any()))
                .thenAnswer(invocation -> new LinkedHashSet<>(invocation.getArgument(1)));

        WeeklyReviewReadableAssociations result = resolver.resolve(7L, List.of(review));

        assertEquals(501, result.taskIdsFor(7001L).size());
        verify(permissionService, times(2)).filterReadableTaskIds(eq(7L), any());
    }

    @Test
    void resolve_shouldRejectRelationForReviewOutsideRequestedSet() {
        WeeklyReview review = review(7001L, null);
        when(weeklyReviewTaskMapper.selectByReviewIds(any()))
                .thenReturn(List.of(relation(9999L, 101L)));

        assertThrows(BusinessException.class, () -> resolver.resolve(7L, List.of(review)));
    }

    private WeeklyReview review(Long id, Long focusProjectId) {
        WeeklyReview review = new WeeklyReview();
        review.setId(id);
        review.setFocusProjectId(focusProjectId);
        return review;
    }

    private WeeklyReviewTask relation(Long reviewId, Long taskId) {
        WeeklyReviewTask relation = new WeeklyReviewTask();
        relation.setWeeklyReviewId(reviewId);
        relation.setTaskId(taskId);
        return relation;
    }
}
