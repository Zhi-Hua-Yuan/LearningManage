package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.constant.TeamRoleEnum;
import com.spt.learningmanage.constant.SystemRoleEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.exception.PermissionDeniedException;
import com.spt.learningmanage.mapper.PermissionQueryMapper;
import com.spt.learningmanage.model.permission.ActorPermissionRow;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.model.permission.ProjectPermissionRow;
import com.spt.learningmanage.model.permission.TaskPermissionRow;
import com.spt.learningmanage.model.permission.TeamMemberPermissionRow;
import com.spt.learningmanage.model.permission.WeeklyReviewPermissionRow;
import com.spt.learningmanage.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PermissionServiceImplTest {

    @Mock
    private PermissionQueryMapper permissionQueryMapper;

    @InjectMocks
    private PermissionServiceImpl permissionService;

    @BeforeEach
    void defaultActorIsActive() {
        lenient().when(permissionQueryMapper.selectActorPermissionRow(any()))
                .thenAnswer(invocation -> activeActor(invocation.getArgument(0), SystemRoleEnum.USER));
    }

    @Test
    void personalProjectOwnerCanViewAndManage() {
        when(permissionQueryMapper.selectProjectPermissionRows(10L, List.of(100L)))
                .thenReturn(List.of(personalProject(100L, 10L)));

        ProjectAccessScope scope = permissionService.requireProjectView(10L, 100L);

        assertTrue(scope.isPersonalProject());
        assertTrue(scope.canManage());
        permissionService.requireProjectManage(10L, 100L);
    }

    @Test
    void personalProjectOutsiderIsDenied() {
        when(permissionQueryMapper.selectProjectPermissionRows(20L, List.of(100L)))
                .thenReturn(List.of(personalProject(100L, 10L)));

        PermissionDeniedException exception = assertThrows(
                PermissionDeniedException.class,
                () -> permissionService.requireProjectView(20L, 100L)
        );

        assertEquals(ErrorCode.FORBIDDEN_ERROR, exception.getErrorCode());
    }

    @Test
    void teamMemberCanViewButCannotManageProject() {
        when(permissionQueryMapper.selectProjectPermissionRows(20L, List.of(100L)))
                .thenReturn(List.of(teamProject(100L, 10L, 200L, TeamRoleEnum.MEMBER)));

        permissionService.requireProjectView(20L, 100L);

        assertThrows(
                PermissionDeniedException.class,
                () -> permissionService.requireProjectManage(20L, 100L)
        );
    }

    @Test
    void teamAdminCanManageProject() {
        when(permissionQueryMapper.selectProjectPermissionRows(20L, List.of(100L)))
                .thenReturn(List.of(teamProject(100L, 10L, 200L, TeamRoleEnum.ADMIN)));

        permissionService.requireProjectManage(20L, 100L);
    }

    @Test
    void assignedMemberCanEditAndChangeTaskStatus() {
        TaskPermissionRow row = teamTask(501L, 10L, 20L, 200L, TeamRoleEnum.MEMBER);
        when(permissionQueryMapper.selectTaskPermissionRows(20L, List.of(501L)))
                .thenReturn(List.of(row));

        permissionService.requireTaskEditContent(20L, 501L);
        permissionService.requireTaskChangeStatus(20L, 501L);

        assertThrows(
                PermissionDeniedException.class,
                () -> permissionService.requireTaskAssign(20L, 501L)
        );
    }

    @Test
    void nonAssigneeMemberCanViewButCannotEditTask() {
        TaskPermissionRow row = teamTask(501L, 10L, 30L, 200L, TeamRoleEnum.MEMBER);
        when(permissionQueryMapper.selectTaskPermissionRows(20L, List.of(501L)))
                .thenReturn(List.of(row));

        permissionService.requireTaskView(20L, 501L);

        assertThrows(
                PermissionDeniedException.class,
                () -> permissionService.requireTaskEditContent(20L, 501L)
        );
    }

    @Test
    void teamAdminCanReorganizeAndDeleteTask() {
        TaskPermissionRow row = teamTask(501L, 10L, 30L, 200L, TeamRoleEnum.ADMIN);
        when(permissionQueryMapper.selectTaskPermissionRows(20L, List.of(501L)))
                .thenReturn(List.of(row));

        permissionService.requireTaskReorganize(20L, 501L);
        permissionService.requireTaskDelete(20L, 501L);
    }

    @Test
    void reviewAuthorCanReadFullContentButTeamMemberCannotReadPrivateReview() {
        WeeklyReviewPermissionRow privateReview = review(901L, 10L, "PRIVATE", null, null);
        when(permissionQueryMapper.selectWeeklyReviewPermissionRows(10L, List.of(901L)))
                .thenReturn(List.of(privateReview));
        permissionService.requireWeeklyReviewFullView(10L, 901L);

        when(permissionQueryMapper.selectWeeklyReviewPermissionRows(20L, List.of(901L)))
                .thenReturn(List.of(privateReview));
        assertThrows(
                PermissionDeniedException.class,
                () -> permissionService.requireWeeklyReviewSharedView(20L, 901L)
        );
    }

    @Test
    void activeTeamMemberCanReadTeamSummary() {
        WeeklyReviewPermissionRow teamReview = review(901L, 10L, "TEAM", 200L, TeamRoleEnum.MEMBER);
        when(permissionQueryMapper.selectWeeklyReviewPermissionRows(20L, List.of(901L)))
                .thenReturn(List.of(teamReview));

        permissionService.requireWeeklyReviewSharedView(20L, 901L);
    }

    @Test
    void systemAdminDoesNotBypassResourceMembership() {
        when(permissionQueryMapper.selectActorPermissionRow(99L))
                .thenReturn(activeActor(99L, SystemRoleEnum.SYSTEM_ADMIN));
        when(permissionQueryMapper.selectProjectPermissionRows(99L, List.of(100L)))
                .thenReturn(List.of(teamProject(100L, 10L, 200L, null)));

        assertThrows(
                PermissionDeniedException.class,
                () -> permissionService.requireProjectView(99L, 100L)
        );
    }

    @Test
    void onlyOwnerCanChangeTeamMemberRole() {
        when(permissionQueryMapper.selectTeamMemberPermissionRow(10L, 200L, 20L))
                .thenReturn(teamMember(200L, 10L, 10L, TeamRoleEnum.OWNER, 20L, TeamRoleEnum.MEMBER));
        permissionService.requireTeamMemberRoleUpdate(10L, 200L, 20L);

        when(permissionQueryMapper.selectTeamMemberPermissionRow(20L, 200L, 30L))
                .thenReturn(teamMember(200L, 10L, 20L, TeamRoleEnum.ADMIN, 30L, TeamRoleEnum.MEMBER));
        assertThrows(
                PermissionDeniedException.class,
                () -> permissionService.requireTeamMemberRoleUpdate(20L, 200L, 30L)
        );
    }

    @Test
    void adminCanRemoveMemberButNotAdmin() {
        when(permissionQueryMapper.selectTeamMemberPermissionRow(20L, 200L, 30L))
                .thenReturn(teamMember(200L, 10L, 20L, TeamRoleEnum.ADMIN, 30L, TeamRoleEnum.MEMBER));
        permissionService.requireTeamMemberRemove(20L, 200L, 30L);

        when(permissionQueryMapper.selectTeamMemberPermissionRow(20L, 200L, 40L))
                .thenReturn(teamMember(200L, 10L, 20L, TeamRoleEnum.ADMIN, 40L, TeamRoleEnum.ADMIN));
        assertThrows(
                PermissionDeniedException.class,
                () -> permissionService.requireTeamMemberRemove(20L, 200L, 40L)
        );
    }

    @Test
    void ownerCannotLeaveAndMemberCanLeave() {
        when(permissionQueryMapper.selectTeamMemberPermissionRow(10L, 200L, 10L))
                .thenReturn(teamMember(200L, 10L, 10L, TeamRoleEnum.OWNER, 10L, TeamRoleEnum.OWNER));
        assertThrows(
                PermissionDeniedException.class,
                () -> permissionService.requireTeamLeave(10L, 200L)
        );

        when(permissionQueryMapper.selectTeamMemberPermissionRow(20L, 200L, 20L))
                .thenReturn(teamMember(200L, 10L, 20L, TeamRoleEnum.MEMBER, 20L, TeamRoleEnum.MEMBER));
        permissionService.requireTeamLeave(20L, 200L);
    }

    @Test
    void missingOrInvalidFactsFailClosed() {
        when(permissionQueryMapper.selectProjectPermissionRows(10L, List.of(100L)))
                .thenReturn(List.of());
        assertThrows(
                PermissionDeniedException.class,
                () -> permissionService.requireProjectView(10L, 100L)
        );

        when(permissionQueryMapper.selectProjectPermissionRows(10L, List.of(101L)))
                .thenReturn(List.of(teamProject(101L, 10L, 200L, null)));
        assertThrows(
                PermissionDeniedException.class,
                () -> permissionService.requireProjectView(10L, 101L)
        );

        when(permissionQueryMapper.selectTeamMemberPermissionRow(20L, 200L, 20L))
                .thenReturn(null);
        assertThrows(
                PermissionDeniedException.class,
                () -> permissionService.requireTeamLeave(20L, 200L)
        );

        TeamMemberPermissionRow deletedMembership = teamMember(
                200L, 10L, 20L, TeamRoleEnum.MEMBER, 20L, TeamRoleEnum.MEMBER
        );
        deletedMembership.setActorMembershipDeletedAt(LocalDateTime.now());
        when(permissionQueryMapper.selectTeamMemberPermissionRow(20L, 200L, 20L))
                .thenReturn(deletedMembership);
        assertThrows(
                PermissionDeniedException.class,
                () -> permissionService.requireTeamLeave(20L, 200L)
        );
    }

    @Test
    void nullActorIsNotLoginAndInvalidIdIsParamsError() {
        BusinessException notLogin = assertThrows(
                BusinessException.class,
                () -> permissionService.requireProjectView(null, 100L)
        );
        assertEquals(ErrorCode.NOT_LOGIN_ERROR, notLogin.getErrorCode());

        BusinessException invalidId = assertThrows(
                BusinessException.class,
                () -> permissionService.requireProjectView(10L, 0L)
        );
        assertEquals(ErrorCode.PARAMS_ERROR, invalidId.getErrorCode());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("permissionCalls")
    void deletedActorIsRejectedBeforeAnyResourceLookup(
            String operation,
            Consumer<PermissionService> call
    ) {
        when(permissionQueryMapper.selectActorPermissionRow(77L))
                .thenReturn(actor(77L, SystemRoleEnum.USER, 1));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> call.accept(permissionService)
        );

        assertEquals(ErrorCode.NOT_LOGIN_ERROR, exception.getErrorCode(), operation);
        verify(permissionQueryMapper, never()).selectProjectPermissionRows(any(), any());
        verify(permissionQueryMapper, never()).selectTaskPermissionRows(any(), any());
        verify(permissionQueryMapper, never()).selectWeeklyReviewPermissionRows(any(), any());
        verify(permissionQueryMapper, never()).selectTeamMemberPermissionRow(any(), any(), any());
    }

    private static Stream<Arguments> permissionCalls() {
        return Stream.of(
                Arguments.of("projectView", (Consumer<PermissionService>) service -> service.requireProjectView(77L, 100L)),
                Arguments.of("projectCreateTask", (Consumer<PermissionService>) service -> service.requireProjectCreateTask(77L, 100L)),
                Arguments.of("projectManage", (Consumer<PermissionService>) service -> service.requireProjectManage(77L, 100L)),
                Arguments.of("projectMemberList", (Consumer<PermissionService>) service -> service.requireProjectMemberList(77L, 100L)),
                Arguments.of("taskView", (Consumer<PermissionService>) service -> service.requireTaskView(77L, 501L)),
                Arguments.of("taskEditContent", (Consumer<PermissionService>) service -> service.requireTaskEditContent(77L, 501L)),
                Arguments.of("taskChangeStatus", (Consumer<PermissionService>) service -> service.requireTaskChangeStatus(77L, 501L)),
                Arguments.of("taskReorganize", (Consumer<PermissionService>) service -> service.requireTaskReorganize(77L, 501L)),
                Arguments.of("taskAssign", (Consumer<PermissionService>) service -> service.requireTaskAssign(77L, 501L)),
                Arguments.of("taskDelete", (Consumer<PermissionService>) service -> service.requireTaskDelete(77L, 501L)),
                Arguments.of("taskAssignmentHistory", (Consumer<PermissionService>) service -> service.requireTaskAssignmentHistoryView(77L, 501L)),
                Arguments.of("reviewFullView", (Consumer<PermissionService>) service -> service.requireWeeklyReviewFullView(77L, 901L)),
                Arguments.of("reviewUpdate", (Consumer<PermissionService>) service -> service.requireWeeklyReviewUpdate(77L, 901L)),
                Arguments.of("reviewDelete", (Consumer<PermissionService>) service -> service.requireWeeklyReviewDelete(77L, 901L)),
                Arguments.of("reviewSharedView", (Consumer<PermissionService>) service -> service.requireWeeklyReviewSharedView(77L, 901L)),
                Arguments.of("teamRoleUpdate", (Consumer<PermissionService>) service -> service.requireTeamMemberRoleUpdate(77L, 200L, 20L)),
                Arguments.of("teamMemberRemove", (Consumer<PermissionService>) service -> service.requireTeamMemberRemove(77L, 200L, 20L)),
                Arguments.of("teamLeave", (Consumer<PermissionService>) service -> service.requireTeamLeave(77L, 200L))
        );
    }

    @Test
    void unknownSystemRoleIsDeniedWithoutResourceLookup() {
        when(permissionQueryMapper.selectActorPermissionRow(77L))
                .thenReturn(actor(77L, "UNKNOWN", 0));

        PermissionDeniedException exception = assertThrows(
                PermissionDeniedException.class,
                () -> permissionService.requireProjectView(77L, 100L)
        );

        assertEquals(ErrorCode.FORBIDDEN_ERROR, exception.getErrorCode());
        verify(permissionQueryMapper, never()).selectProjectPermissionRows(any(), any());
    }

    @Test
    void missingActorFactIsTreatedAsExpiredLogin() {
        when(permissionQueryMapper.selectActorPermissionRow(77L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> permissionService.requireProjectView(77L, 100L)
        );

        assertEquals(ErrorCode.NOT_LOGIN_ERROR, exception.getErrorCode());
        verify(permissionQueryMapper, never()).selectProjectPermissionRows(any(), any());
    }

    @ParameterizedTest(name = "invalid role: {0}")
    @MethodSource("invalidSystemRoles")
    void nonCanonicalSystemRoleIsDenied(String role) {
        when(permissionQueryMapper.selectActorPermissionRow(77L))
                .thenReturn(actor(77L, role, 0));

        assertThrows(
                PermissionDeniedException.class,
                () -> permissionService.requireProjectView(77L, 100L)
        );
        verify(permissionQueryMapper, never()).selectProjectPermissionRows(any(), any());
    }

    private static Stream<Arguments> invalidSystemRoles() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of("user"),
                Arguments.of("admin"),
                Arguments.of("UNKNOWN")
        );
    }

    @Test
    void ownerRoleMustMatchTeamOwnerId() {
        ProjectPermissionRow row = teamProject(100L, 10L, 200L, TeamRoleEnum.OWNER);
        row.setTeamOwnerUserId(10L);
        row.setActorTeamMemberId(1L);
        when(permissionQueryMapper.selectProjectPermissionRows(20L, List.of(100L)))
                .thenReturn(List.of(row));

        assertThrows(
                PermissionDeniedException.class,
                () -> permissionService.requireProjectView(20L, 100L)
        );
    }

    @Test
    void teamOwnerIdMustCarryOwnerRole() {
        ProjectPermissionRow row = teamProject(100L, 10L, 200L, TeamRoleEnum.MEMBER);
        row.setTeamOwnerUserId(20L);
        when(permissionQueryMapper.selectProjectPermissionRows(20L, List.of(100L)))
                .thenReturn(List.of(row));

        assertThrows(
                PermissionDeniedException.class,
                () -> permissionService.requireProjectView(20L, 100L)
        );
    }

    @Test
    void reviewAuthorRetainsAccessAfterTeamLifecycleEnds() {
        WeeklyReviewPermissionRow row = review(901L, 10L, "TEAM", 200L, TeamRoleEnum.MEMBER);
        row.setTeamIsDelete(1);
        row.setActorTeamMemberId(null);
        row.setActorTeamRole(null);
        row.setActorMembershipIsDelete(null);

        when(permissionQueryMapper.selectWeeklyReviewPermissionRows(10L, List.of(901L)))
                .thenReturn(List.of(row));

        permissionService.requireWeeklyReviewFullView(10L, 901L);
        permissionService.requireWeeklyReviewUpdate(10L, 901L);
        permissionService.requireWeeklyReviewDelete(10L, 901L);
        permissionService.requireWeeklyReviewSharedView(10L, 901L);
    }

    @Test
    void nonAuthorCannotReadSharedReviewAfterTeamLifecycleEnds() {
        WeeklyReviewPermissionRow row = review(901L, 10L, "TEAM", 200L, TeamRoleEnum.MEMBER);
        row.setTeamIsDelete(1);
        when(permissionQueryMapper.selectWeeklyReviewPermissionRows(20L, List.of(901L)))
                .thenReturn(List.of(row));

        assertThrows(
                PermissionDeniedException.class,
                () -> permissionService.requireWeeklyReviewSharedView(20L, 901L)
        );
    }

    @Test
    void structurallyInvalidReviewIsDeniedEvenToAuthor() {
        WeeklyReviewPermissionRow row = review(901L, 10L, "PRIVATE", 200L, null);
        when(permissionQueryMapper.selectWeeklyReviewPermissionRows(10L, List.of(901L)))
                .thenReturn(List.of(row));

        assertThrows(
                PermissionDeniedException.class,
                () -> permissionService.requireWeeklyReviewFullView(10L, 901L)
        );
    }

    private ActorPermissionRow activeActor(Long actorId, SystemRoleEnum role) {
        return actor(actorId, role, 0);
    }

    private ActorPermissionRow actor(Long actorId, SystemRoleEnum role, Integer isDelete) {
        ActorPermissionRow row = new ActorPermissionRow();
        row.setActorUserId(actorId);
        row.setActorSystemRole(role.getValue());
        row.setActorIsDelete(isDelete);
        return row;
    }

    private ActorPermissionRow actor(Long actorId, String role, Integer isDelete) {
        ActorPermissionRow row = new ActorPermissionRow();
        row.setActorUserId(actorId);
        row.setActorSystemRole(role);
        row.setActorIsDelete(isDelete);
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
            Long ownerId,
            Long teamId,
            TeamRoleEnum role
    ) {
        ProjectPermissionRow row = personalProject(projectId, ownerId);
        row.setTeamId(teamId);
        row.setTeamOwnerUserId(ownerId);
        row.setTeamIsDelete(0);
        row.setActorTeamMemberId(role == null ? null : 1L);
        row.setActorTeamRole(role == null ? null : role.getValue());
        row.setActorMembershipIsDelete(role == null ? null : 0);
        return row;
    }

    private TaskPermissionRow teamTask(
            Long taskId,
            Long creatorId,
            Long assigneeId,
            Long teamId,
            TeamRoleEnum role
    ) {
        TaskPermissionRow row = new TaskPermissionRow();
        row.setTaskId(taskId);
        row.setTaskCreatorUserId(creatorId);
        row.setAssigneeUserId(assigneeId);
        row.setProjectId(100L);
        row.setTaskIsDelete(0);
        row.setProjectOwnerUserId(creatorId);
        row.setProjectIsDelete(0);
        row.setTeamId(teamId);
        row.setTeamOwnerUserId(creatorId);
        row.setTeamIsDelete(0);
        row.setActorTeamMemberId(role == null ? null : 1L);
        row.setActorTeamRole(role == null ? null : role.getValue());
        row.setActorMembershipIsDelete(role == null ? null : 0);
        return row;
    }

    private WeeklyReviewPermissionRow review(
            Long reviewId,
            Long authorId,
            String scope,
            Long teamId,
            TeamRoleEnum role
    ) {
        WeeklyReviewPermissionRow row = new WeeklyReviewPermissionRow();
        row.setReviewId(reviewId);
        row.setAuthorUserId(authorId);
        row.setVisibilityScope(scope);
        row.setTeamId(teamId);
        if (teamId != null) {
            row.setTeamOwnerUserId(authorId);
            row.setTeamIsDelete(0);
            row.setActorTeamMemberId(role == null ? null : 1L);
            row.setActorTeamRole(role == null ? null : role.getValue());
            row.setActorMembershipIsDelete(role == null ? null : 0);
        }
        return row;
    }

    private TeamMemberPermissionRow teamMember(
            Long teamId,
            Long teamOwnerId,
            Long actorId,
            TeamRoleEnum actorRole,
            Long targetId,
            TeamRoleEnum targetRole
    ) {
        TeamMemberPermissionRow row = new TeamMemberPermissionRow();
        row.setTeamId(teamId);
        row.setTeamOwnerUserId(teamOwnerId);
        row.setTeamIsDelete(0);
        row.setActorUserId(actorId);
        row.setActorTeamMemberId(1L);
        row.setActorTeamRole(actorRole.getValue());
        row.setActorMembershipIsDelete(0);
        row.setTargetUserId(targetId);
        row.setTargetTeamMemberId(2L);
        row.setTargetTeamRole(targetRole.getValue());
        row.setTargetMembershipIsDelete(0);
        return row;
    }
}
