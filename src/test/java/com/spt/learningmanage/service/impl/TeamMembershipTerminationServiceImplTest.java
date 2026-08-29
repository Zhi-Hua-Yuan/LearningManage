package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.constant.TaskAssignmentActionEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.exception.PermissionDeniedException;
import com.spt.learningmanage.mapper.TaskAssignmentLogMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.TeamMemberMapper;
import com.spt.learningmanage.model.dto.team.TeamMemberRemoveRequest;
import com.spt.learningmanage.model.entity.TaskAssignmentLog;
import com.spt.learningmanage.model.entity.TeamMember;
import com.spt.learningmanage.model.query.team.MembershipTaskCleanupRow;
import com.spt.learningmanage.model.vo.team.TeamMembershipTerminationVO;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.TeamMembershipTerminationPolicy;
import com.spt.learningmanage.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class TeamMembershipTerminationServiceImplTest {

    @Mock private TeamMemberMapper teamMemberMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private TaskAssignmentLogMapper taskAssignmentLogMapper;
    @Mock private PermissionService permissionService;
    @Mock private TeamMembershipTerminationPolicy terminationPolicy;
    @InjectMocks private TeamMembershipTerminationServiceImpl service;

    @BeforeEach
    void setUp() {
        UserHolder.set(11L);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
    }

    @Test
    void leaveUnassignsTasksWritesLogsAndCasTerminatesMember() {
        TeamMember target = member(301L, 7L, 11L, "MEMBER");
        when(teamMemberMapper.selectActiveMembersForUpdate(7L, List.of(11L)))
                .thenReturn(List.of(target));
        when(terminationPolicy.requireLeaveAllowed(11L, 7L, List.of(target)))
                .thenReturn(target);
        when(taskMapper.selectIncompleteAssignedTeamTasksForUpdate(7L, 11L))
                .thenReturn(List.of(task(701L, 11L), task(702L, 11L)));
        when(taskMapper.bulkUnassignIncompleteTeamTasks(eq(7L), eq(11L),
                eq(List.of(701L, 702L)), eq(11L), any())).thenReturn(2);
        when(taskAssignmentLogMapper.batchInsertMembershipTerminationLogs(anyList()))
                .thenReturn(2);
        when(teamMemberMapper.deactivateMembershipCas(eq(301L), eq(7L), eq(11L),
                eq("MEMBER"), any())).thenReturn(1);

        TeamMembershipTerminationVO result = service.leaveTeam(7L);

        assertEquals(7L, result.getTeamId());
        assertEquals(11L, result.getMemberUserId());
        assertEquals("MEMBER_LEFT", result.getAction());
        assertEquals(2, result.getUnassignedTaskCount());
        assertNotNull(result.getTerminatedAt());
        ArgumentCaptor<List<TaskAssignmentLog>> logs = ArgumentCaptor.forClass(List.class);
        verify(taskAssignmentLogMapper).batchInsertMembershipTerminationLogs(logs.capture());
        assertEquals(2, logs.getValue().size());
        assertEquals(TaskAssignmentActionEnum.MEMBER_LEFT.getValue(),
                logs.getValue().get(0).getAction());
        assertEquals(11L, logs.getValue().get(0).getFromAssigneeUserId());
        assertEquals(11L, logs.getValue().get(0).getAssignedByUserId());
        assertEquals(result.getTerminatedAt(), logs.getValue().get(0).getCreateTime());
        verify(teamMemberMapper).deactivateMembershipCas(301L, 7L, 11L,
                "MEMBER", result.getTerminatedAt());
    }

    @Test
    void adminRemovalUsesActorAsAuditOperatorAndDifferentAction() {
        UserHolder.set(21L);
        TeamMember target = member(302L, 7L, 22L, "MEMBER");
        when(teamMemberMapper.selectActiveMembersForUpdate(7L, List.of(21L, 22L)))
                .thenReturn(List.of(member(321L, 7L, 21L, "ADMIN"), target));
        when(terminationPolicy.requireRemoveAllowed(eq(21L), eq(7L), eq(22L), any()))
                .thenReturn(target);
        when(taskMapper.selectIncompleteAssignedTeamTasksForUpdate(7L, 22L))
                .thenReturn(List.of(task(703L, 22L)));
        when(taskMapper.bulkUnassignIncompleteTeamTasks(eq(7L), eq(22L),
                eq(List.of(703L)), eq(21L), any())).thenReturn(1);
        when(taskAssignmentLogMapper.batchInsertMembershipTerminationLogs(anyList()))
                .thenReturn(1);
        when(teamMemberMapper.deactivateMembershipCas(eq(302L), eq(7L), eq(22L),
                eq("MEMBER"), any())).thenReturn(1);

        TeamMemberRemoveRequest request = request(7L, 22L);
        TeamMembershipTerminationVO result = service.removeMember(request);

        assertEquals("MEMBER_REMOVED", result.getAction());
        ArgumentCaptor<List<TaskAssignmentLog>> logs = ArgumentCaptor.forClass(List.class);
        verify(taskAssignmentLogMapper).batchInsertMembershipTerminationLogs(logs.capture());
        assertEquals(21L, logs.getValue().get(0).getAssignedByUserId());
        assertEquals(TaskAssignmentActionEnum.MEMBER_REMOVED.getValue(),
                logs.getValue().get(0).getAction());
    }

    @Test
    void noAssignedTasksStillCasTerminatesAndDoesNotWriteAudit() {
        TeamMember target = member(303L, 7L, 11L, "ADMIN");
        when(teamMemberMapper.selectActiveMembersForUpdate(7L, List.of(11L)))
                .thenReturn(List.of(target));
        when(terminationPolicy.requireLeaveAllowed(11L, 7L, List.of(target)))
                .thenReturn(target);
        when(taskMapper.selectIncompleteAssignedTeamTasksForUpdate(7L, 11L))
                .thenReturn(List.of());
        when(teamMemberMapper.deactivateMembershipCas(eq(303L), eq(7L), eq(11L),
                eq("ADMIN"), any())).thenReturn(1);

        TeamMembershipTerminationVO result = service.leaveTeam(7L);

        assertEquals(0, result.getUnassignedTaskCount());
        verify(taskMapper, never()).bulkUnassignIncompleteTeamTasks(any(), any(),
                any(), any(), any());
        verify(taskAssignmentLogMapper, never()).batchInsertMembershipTerminationLogs(anyList());
    }

    @Test
    void staleTaskUpdateAbortsBeforeAuditAndMembershipCas() {
        TeamMember target = member(304L, 7L, 11L, "MEMBER");
        when(teamMemberMapper.selectActiveMembersForUpdate(7L, List.of(11L)))
                .thenReturn(List.of(target));
        when(terminationPolicy.requireLeaveAllowed(11L, 7L, List.of(target)))
                .thenReturn(target);
        when(taskMapper.selectIncompleteAssignedTeamTasksForUpdate(7L, 11L))
                .thenReturn(List.of(task(704L, 11L)));
        when(taskMapper.bulkUnassignIncompleteTeamTasks(eq(7L), eq(11L),
                eq(List.of(704L)), eq(11L), any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.leaveTeam(7L));
        assertEquals(ErrorCode.OPERATION_ERROR, ex.getErrorCode());
        verify(taskAssignmentLogMapper, never()).batchInsertMembershipTerminationLogs(anyList());
        verify(teamMemberMapper, never()).deactivateMembershipCas(any(), any(), any(), any(), any());
    }

    @Test
    void auditFailureAbortsBeforeMembershipCas() {
        TeamMember target = member(305L, 7L, 11L, "MEMBER");
        when(teamMemberMapper.selectActiveMembersForUpdate(7L, List.of(11L)))
                .thenReturn(List.of(target));
        when(terminationPolicy.requireLeaveAllowed(11L, 7L, List.of(target)))
                .thenReturn(target);
        when(taskMapper.selectIncompleteAssignedTeamTasksForUpdate(7L, 11L))
                .thenReturn(List.of(task(705L, 11L)));
        when(taskMapper.bulkUnassignIncompleteTeamTasks(eq(7L), eq(11L),
                eq(List.of(705L)), eq(11L), any())).thenReturn(1);
        when(taskAssignmentLogMapper.batchInsertMembershipTerminationLogs(anyList()))
                .thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.leaveTeam(7L));
        assertEquals(ErrorCode.SYSTEM_ERROR, ex.getErrorCode());
        verify(teamMemberMapper, never()).deactivateMembershipCas(any(), any(), any(), any(), any());
    }

    @Test
    void precheckPermissionFailureDoesNotLeakMemberOrTaskState() {
        doThrow(new PermissionDeniedException())
                .when(permissionService).requireTeamLeave(11L, 7L);

        assertThrows(PermissionDeniedException.class, () -> service.leaveTeam(7L));

        verifyNoInteractions(teamMemberMapper, taskMapper, taskAssignmentLogMapper,
                terminationPolicy);
    }

    @Test
    void repeatedInactiveRemovalIsRejectedBeforeLocking() {
        TeamMemberRemoveRequest request = request(7L, 22L);
        doThrow(new PermissionDeniedException())
                .when(permissionService).requireTeamMemberRemove(11L, 7L, 22L);

        assertThrows(PermissionDeniedException.class, () -> service.removeMember(request));

        verifyNoInteractions(teamMemberMapper, taskMapper, taskAssignmentLogMapper,
                terminationPolicy);
    }

    @Test
    void transactionMethodsAreIndependentPublicRollbackBoundaries() throws Exception {
        Transactional leave = TeamMembershipTerminationServiceImpl.class
                .getMethod("leaveTeam", Long.class).getAnnotation(Transactional.class);
        Transactional remove = TeamMembershipTerminationServiceImpl.class
                .getMethod("removeMember", TeamMemberRemoveRequest.class)
                .getAnnotation(Transactional.class);
        assertNotNull(leave);
        assertNotNull(remove);
        assertEquals(Exception.class, leave.rollbackFor()[0]);
        assertEquals(Exception.class, remove.rollbackFor()[0]);
    }

    private TeamMember member(Long id, Long teamId, Long userId, String role) {
        TeamMember member = new TeamMember();
        member.setId(id);
        member.setTeamId(teamId);
        member.setUserId(userId);
        member.setRole(role);
        member.setIsDelete(0);
        return member;
    }

    private MembershipTaskCleanupRow task(Long taskId, Long assigneeUserId) {
        MembershipTaskCleanupRow row = new MembershipTaskCleanupRow();
        row.setTaskId(taskId);
        row.setAssigneeUserId(assigneeUserId);
        return row;
    }

    private TeamMemberRemoveRequest request(Long teamId, Long targetUserId) {
        TeamMemberRemoveRequest request = new TeamMemberRemoveRequest();
        request.setTeamId(teamId);
        request.setTargetUserId(targetUserId);
        return request;
    }
}
