package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.constant.SystemRoleEnum;
import com.spt.learningmanage.constant.TeamRoleEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.PermissionDeniedException;
import com.spt.learningmanage.mapper.PermissionQueryMapper;
import com.spt.learningmanage.model.permission.ActorPermissionRow;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.model.permission.ProjectPermissionRow;
import com.spt.learningmanage.model.permission.TaskCapabilities;
import com.spt.learningmanage.model.permission.TaskPermissionRow;
import com.spt.learningmanage.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceBatchTest {

    private static final long ACTOR_ID = 20L;

    @Mock
    private PermissionQueryMapper permissionQueryMapper;

    @InjectMocks
    private PermissionServiceImpl permissionService;

    @BeforeEach
    void activeActorByDefault() {
        lenient().when(permissionQueryMapper.selectActorPermissionRow(any()))
                .thenAnswer(invocation -> actor(invocation.getArgument(0), SystemRoleEnum.USER));
    }

    @Test
    void resolveProjectScopesFiltersUnauthorizedRowsInRequestOrder() {
        List<Long> ids = List.of(301L, 302L, 303L, 301L);
        when(permissionQueryMapper.selectProjectPermissionRows(ACTOR_ID, List.of(301L, 302L, 303L)))
                .thenReturn(List.of(
                        personalProject(301L, ACTOR_ID),
                        teamProject(302L, 900L, ACTOR_ID, TeamRoleEnum.MEMBER),
                        personalProject(303L, 999L)
                ));

        Map<Long, ProjectAccessScope> result = permissionService.resolveProjectScopes(ACTOR_ID, ids);

        assertEquals(List.of(301L, 302L), new ArrayList<>(result.keySet()));
        assertTrue(result.get(302L).isTeamProject());
        assertThrows(UnsupportedOperationException.class, () -> result.put(999L, result.get(301L)));
        verify(permissionQueryMapper, times(1))
                .selectProjectPermissionRows(ACTOR_ID, List.of(301L, 302L, 303L));
    }

    @Test
    void hundredTaskIdsUseOneActorAndOneResourceQuery() {
        List<Long> ids = LongStream.rangeClosed(1, 100).boxed().toList();
        List<TaskPermissionRow> rows = ids.stream()
                .map(id -> personalTask(id, ACTOR_ID))
                .toList();
        when(permissionQueryMapper.selectTaskPermissionRows(ACTOR_ID, ids)).thenReturn(rows);

        Set<Long> readable = permissionService.filterReadableTaskIds(ACTOR_ID, ids);

        assertEquals(ids, new ArrayList<>(readable));
        verify(permissionQueryMapper, times(1)).selectActorPermissionRow(ACTOR_ID);
        verify(permissionQueryMapper, times(1)).selectTaskPermissionRows(ACTOR_ID, ids);
    }

    @Test
    void capabilitiesFollowFrozenTaskMatrix() {
        List<Long> ids = List.of(401L, 402L, 403L, 404L, 405L);
        when(permissionQueryMapper.selectTaskPermissionRows(ACTOR_ID, ids)).thenReturn(List.of(
                personalTask(401L, ACTOR_ID),
                teamTask(402L, ACTOR_ID, 900L, TeamRoleEnum.OWNER, 999L),
                teamTask(403L, ACTOR_ID, 900L, TeamRoleEnum.ADMIN, 999L),
                teamTask(404L, ACTOR_ID, 900L, TeamRoleEnum.MEMBER, ACTOR_ID),
                teamTask(405L, ACTOR_ID, 900L, TeamRoleEnum.MEMBER, 999L)
        ));

        Map<Long, TaskCapabilities> result = permissionService.resolveTaskCapabilities(ACTOR_ID, ids);

        assertTrue(result.get(401L).canDelete());
        assertTrue(result.get(402L).canAssign());
        assertTrue(result.get(403L).canReorganize());
        assertTrue(result.get(404L).canEditContent());
        assertTrue(result.get(404L).canChangeStatus());
        assertFalse(result.get(404L).canAssign());
        assertFalse(result.get(405L).canEditContent());
        assertFalse(result.get(405L).canDelete());
        verify(permissionQueryMapper, times(1)).selectTaskPermissionRows(ACTOR_ID, ids);
    }

    @Test
    void requireAllTasksReadableRejectsOneUnauthorizedIdAsWholeRequest() {
        List<Long> ids = List.of(501L, 502L, 503L);
        when(permissionQueryMapper.selectTaskPermissionRows(ACTOR_ID, ids)).thenReturn(List.of(
                personalTask(501L, ACTOR_ID),
                personalTask(502L, 999L)
        ));

        assertThrows(
                PermissionDeniedException.class,
                () -> permissionService.requireAllTasksReadable(ACTOR_ID, ids)
        );
        verify(permissionQueryMapper, times(1)).selectTaskPermissionRows(ACTOR_ID, ids);
    }

    @Test
    void requireAllTasksReadableReturnsNormalizedImmutableIdsWhenAllPass() {
        List<Long> ids = List.of(601L, 602L, 601L);
        when(permissionQueryMapper.selectTaskPermissionRows(ACTOR_ID, List.of(601L, 602L)))
                .thenReturn(List.of(personalTask(601L, ACTOR_ID), personalTask(602L, ACTOR_ID)));

        Set<Long> result = permissionService.requireAllTasksReadable(ACTOR_ID, ids);

        assertEquals(List.of(601L, 602L), new ArrayList<>(result));
        assertThrows(UnsupportedOperationException.class, () -> result.add(603L));
    }

    @Test
    void batchInputIsValidatedBeforeDatabaseLookup() {
        assertThrows(
                BusinessException.class,
                () -> permissionService.filterReadableTaskIds(ACTOR_ID, List.of(701L, 0L))
        );
        assertThrows(
                BusinessException.class,
                () -> permissionService.filterReadableTaskIds(ACTOR_ID, null)
        );
        assertThrows(
                BusinessException.class,
                () -> permissionService.filterReadableTaskIds(ACTOR_ID,
                        LongStream.rangeClosed(1, 501).boxed().toList())
        );
        verify(permissionQueryMapper, times(0)).selectActorPermissionRow(ACTOR_ID);
        verify(permissionQueryMapper, times(0)).selectTaskPermissionRows(any(), any());
    }

    @Test
    void emptyBatchVerifiesActorAndReturnsImmutableEmptyResult() {
        Set<Long> result = permissionService.filterReadableTaskIds(ACTOR_ID, List.of());

        assertTrue(result.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.add(1L));
        verify(permissionQueryMapper, times(1)).selectActorPermissionRow(ACTOR_ID);
        verify(permissionQueryMapper, times(0)).selectTaskPermissionRows(any(), any());
    }

    @Test
    void ambiguousMapperFactsFailClosed() {
        when(permissionQueryMapper.selectTaskPermissionRows(ACTOR_ID, List.of(801L)))
                .thenReturn(List.of(personalTask(801L, ACTOR_ID), personalTask(801L, ACTOR_ID)));

        assertThrows(
                PermissionDeniedException.class,
                () -> permissionService.filterReadableTaskIds(ACTOR_ID, List.of(801L))
        );
    }

    private ActorPermissionRow actor(Long userId, SystemRoleEnum role) {
        ActorPermissionRow row = new ActorPermissionRow();
        row.setActorUserId(userId);
        row.setActorSystemRole(role.getValue());
        row.setActorIsDelete(0);
        return row;
    }

    private ProjectPermissionRow personalProject(Long projectId, Long ownerId) {
        ProjectPermissionRow row = new ProjectPermissionRow();
        row.setProjectId(projectId);
        row.setProjectOwnerUserId(ownerId);
        row.setProjectIsDelete(0);
        return row;
    }

    private ProjectPermissionRow teamProject(
            Long projectId,
            Long teamId,
            Long actorId,
            TeamRoleEnum role
    ) {
        ProjectPermissionRow row = personalProject(projectId, 999L);
        row.setTeamId(teamId);
        row.setTeamOwnerUserId(999L);
        row.setTeamIsDelete(0);
        row.setActorTeamMemberId(1L);
        row.setActorTeamRole(role.getValue());
        row.setActorMembershipIsDelete(0);
        return row;
    }

    private TaskPermissionRow personalTask(Long taskId, Long ownerId) {
        TaskPermissionRow row = new TaskPermissionRow();
        row.setTaskId(taskId);
        row.setTaskCreatorUserId(ownerId);
        row.setProjectId(1000L + taskId);
        row.setProjectOwnerUserId(ownerId);
        row.setTaskIsDelete(0);
        row.setProjectIsDelete(0);
        return row;
    }

    private TaskPermissionRow teamTask(
            Long taskId,
            Long actorId,
            Long teamId,
            TeamRoleEnum role,
            Long assigneeId
    ) {
        TaskPermissionRow row = personalTask(taskId, 999L);
        row.setAssigneeUserId(assigneeId);
        row.setTeamId(teamId);
        row.setTeamOwnerUserId(role == TeamRoleEnum.OWNER ? actorId : 999L);
        row.setTeamIsDelete(0);
        row.setActorTeamMemberId(1L);
        row.setActorTeamRole(role.getValue());
        row.setActorMembershipIsDelete(0);
        return row;
    }
}
