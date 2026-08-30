package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.constant.TeamRoleEnum;
import com.spt.learningmanage.constant.WeeklyReviewVisibilityScopeEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.PermissionDeniedException;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.model.review.WeeklyReviewAssociationContext;
import com.spt.learningmanage.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyReviewAssociationValidatorTest {

    @Mock
    private PermissionService permissionService;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private ProjectMapper projectMapper;
    @InjectMocks
    private WeeklyReviewAssociationValidatorImpl validator;

    @Test
    void validatesAndDeduplicatesAssociationsWithBatchQueries() {
        Task task = task(101L, 11L);
        Project project = project(11L, "个人项目");
        when(permissionService.requireAllTasksReadable(7L, List.of(101L, 102L)))
                .thenReturn(Set.of(101L, 102L));
        when(taskMapper.selectBatchIds(List.of(101L, 102L)))
                .thenReturn(List.of(task, task(102L, 11L)));
        when(permissionService.resolveProjectScopes(7L, List.of(12L, 11L)))
                .thenReturn(Map.of(
                        11L, new ProjectAccessScope(7L, 11L, 7L, null, null),
                        12L, new ProjectAccessScope(7L, 12L, 7L, null, null)));
        when(projectMapper.selectBatchIds(List.of(12L, 11L))).thenReturn(List.of(
                project(12L, "重点项目"), project));

        WeeklyReviewAssociationContext result = validator.validate(
                7L, WeeklyReviewVisibilityScopeEnum.PRIVATE, null,
                12L, List.of(101L, 102L, 101L));

        assertEquals(12L, result.focusProjectId());
        assertEquals("重点项目", result.focusProjectName());
        assertEquals(List.of(101L, 102L), result.taskIds());
        verify(permissionService, times(1)).requireAllTasksReadable(7L, List.of(101L, 102L));
        verify(permissionService, times(1)).resolveProjectScopes(7L, List.of(12L, 11L));
        verify(taskMapper, times(1)).selectBatchIds(List.of(101L, 102L));
    }

    @Test
    void rejectsCrossTeamTaskForTeamReview() {
        when(permissionService.requireAllTasksReadable(7L, List.of(101L))).thenReturn(Set.of(101L));
        when(taskMapper.selectBatchIds(List.of(101L))).thenReturn(List.of(task(101L, 11L)));
        when(permissionService.resolveProjectScopes(7L, List.of(11L))).thenReturn(Map.of(
                11L, new ProjectAccessScope(7L, 11L, 9L, 99L, TeamRoleEnum.MEMBER)));

        assertThrows(PermissionDeniedException.class, () -> validator.validate(
                7L, WeeklyReviewVisibilityScopeEnum.TEAM, 10L, null, List.of(101L)));
    }

    @Test
    void rejectsMissingTaskEvenWhenPermissionServiceReturnsIt() {
        when(permissionService.requireAllTasksReadable(7L, List.of(101L))).thenReturn(Set.of(101L));
        when(taskMapper.selectBatchIds(List.of(101L))).thenReturn(List.of());

        assertThrows(PermissionDeniedException.class, () -> validator.validate(
                7L, WeeklyReviewVisibilityScopeEnum.PRIVATE, null, null, List.of(101L)));
    }

    @Test
    void rejectsMalformedFocusProjectIdBeforeQueries() {
        assertThrows(BusinessException.class, () -> validator.validate(
                7L, WeeklyReviewVisibilityScopeEnum.PRIVATE, null, 0L, List.of()));
        verify(taskMapper, times(0)).selectBatchIds(any());
        verify(projectMapper, times(0)).selectBatchIds(any());
    }

    private Task task(Long id, Long projectId) {
        Task task = new Task();
        task.setId(id);
        task.setProjectId(projectId);
        return task;
    }

    private Project project(Long id, String name) {
        Project project = new Project();
        project.setId(id);
        project.setName(name);
        return project;
    }
}
