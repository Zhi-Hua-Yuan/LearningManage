package com.spt.learningmanage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spt.learningmanage.constant.SystemRole;
import com.spt.learningmanage.constant.TeamRoleEnum;
import com.spt.learningmanage.exception.PermissionDeniedException;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.TeamMemberMapper;
import com.spt.learningmanage.mapper.UserMapper;
import com.spt.learningmanage.mapper.WeeklyReviewMapper;
import com.spt.learningmanage.model.access.ProjectAccessScope;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.TeamMember;
import com.spt.learningmanage.model.entity.User;
import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.service.impl.PermissionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private TeamMemberMapper teamMemberMapper;
    @Mock
    private WeeklyReviewMapper weeklyReviewMapper;

    private PermissionServiceImpl permissionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        permissionService = new PermissionServiceImpl();
        ReflectionTestUtils.setField(permissionService, "userMapper", userMapper);
        ReflectionTestUtils.setField(permissionService, "projectMapper", projectMapper);
        ReflectionTestUtils.setField(permissionService, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(permissionService, "teamMemberMapper", teamMemberMapper);
        ReflectionTestUtils.setField(permissionService, "weeklyReviewMapper", weeklyReviewMapper);
    }

    @ParameterizedTest
    @CsvSource({"user,USER", "USER,USER", " admin ,SYSTEM_ADMIN", "SYSTEM_ADMIN,SYSTEM_ADMIN"})
    void systemRoleCanonicalizesLegacyAndCanonicalValues(String input, SystemRole expected) {
        assertEquals(expected.name(), SystemRole.canonicalize(input));
    }

    @Test
    void personalProjectOnlyOwnerCanManage() {
        User owner = user(10L, "USER");
        Project project = project(100L, 10L, null);
        when(userMapper.selectById(10L)).thenReturn(owner);
        when(projectMapper.selectBatchIds(Set.of(100L))).thenReturn(List.of(project));

        ProjectAccessScope scope = permissionService.requireProjectManage(10L, 100L);

        assertTrue(scope.projectOwner());
        assertTrue(scope.canManage());
        when(userMapper.selectById(11L)).thenReturn(user(11L, "USER"));
        assertThrows(PermissionDeniedException.class,
                () -> permissionService.requireProjectView(11L, 100L));
    }

    @Test
    void teamMemberCanViewButCannotManageProject() {
        when(userMapper.selectById(11L)).thenReturn(user(11L, "USER"));
        when(projectMapper.selectBatchIds(Set.of(100L))).thenReturn(List.of(project(100L, 10L, 20L)));
        when(teamMemberMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(member(20L, 11L, "MEMBER")));

        ProjectAccessScope scope = permissionService.requireProjectView(11L, 100L);

        assertEquals(TeamRoleEnum.MEMBER, scope.teamRole());
        assertTrue(scope.canView());
        assertTrue(!scope.canManage());
        assertThrows(PermissionDeniedException.class,
                () -> permissionService.requireProjectManage(11L, 100L));
    }

    @Test
    void assignedMemberCanEditContentAndStatusButCannotReorganize() {
        when(userMapper.selectById(11L)).thenReturn(user(11L, "USER"));
        when(taskMapper.selectById(200L)).thenReturn(task(200L, 100L, 11L));
        when(projectMapper.selectBatchIds(Set.of(100L))).thenReturn(List.of(project(100L, 10L, 20L)));
        when(teamMemberMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(member(20L, 11L, "MEMBER")));

        permissionService.requireTaskEditContent(11L, 200L);
        permissionService.requireTaskChangeStatus(11L, 200L);
        assertThrows(PermissionDeniedException.class,
                () -> permissionService.requireTaskReorganize(11L, 200L));
    }

    @Test
    void nonAssigneeMemberCannotEditTask() {
        when(userMapper.selectById(12L)).thenReturn(user(12L, "USER"));
        when(taskMapper.selectById(200L)).thenReturn(task(200L, 100L, 11L));
        when(projectMapper.selectBatchIds(Set.of(100L))).thenReturn(List.of(project(100L, 10L, 20L)));
        when(teamMemberMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(member(20L, 12L, "MEMBER")));

        assertThrows(PermissionDeniedException.class,
                () -> permissionService.requireTaskEditContent(12L, 200L));
    }

    @Test
    void systemAdminDoesNotBypassProjectPrivacy() {
        when(userMapper.selectById(99L)).thenReturn(user(99L, "SYSTEM_ADMIN"));
        when(projectMapper.selectBatchIds(Set.of(100L))).thenReturn(List.of(project(100L, 10L, null)));

        assertThrows(PermissionDeniedException.class,
                () -> permissionService.requireProjectView(99L, 100L));
    }

    @Test
    void privateReviewIsAuthorOnlyAndTeamSummaryRequiresMembership() {
        WeeklyReview privateReview = new WeeklyReview();
        privateReview.setId(300L);
        privateReview.setUserId(10L);
        privateReview.setVisibilityScope("PRIVATE");
        when(weeklyReviewMapper.selectById(300L)).thenReturn(privateReview);
        when(userMapper.selectById(11L)).thenReturn(user(11L, "USER"));

        assertThrows(PermissionDeniedException.class,
                () -> permissionService.requireWeeklyReviewFullView(11L, 300L));

        WeeklyReview teamReview = new WeeklyReview();
        teamReview.setId(301L);
        teamReview.setUserId(10L);
        teamReview.setVisibilityScope("TEAM");
        teamReview.setTeamId(20L);
        when(weeklyReviewMapper.selectById(301L)).thenReturn(teamReview);
        when(teamMemberMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(member(20L, 11L, "MEMBER"));

        permissionService.requireWeeklyReviewSharedView(11L, 301L);
        verify(teamMemberMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void batchTaskFilterUsesBatchTaskAndProjectResolution() {
        when(userMapper.selectById(11L)).thenReturn(user(11L, "USER"));
        when(taskMapper.selectBatchIds(Set.of(200L, 201L)))
                .thenReturn(List.of(task(200L, 100L, 11L), task(201L, 101L, 12L)));
        when(projectMapper.selectBatchIds(Set.of(100L, 101L))).thenReturn(List.of(
                project(100L, 10L, 20L), project(101L, 10L, null)));
        when(teamMemberMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(member(20L, 11L, "MEMBER")));

        assertEquals(Set.of(200L), permissionService.filterReadableTaskIds(11L, Set.of(200L, 201L)));
        verify(taskMapper, times(1)).selectBatchIds(Set.of(200L, 201L));
        verify(projectMapper, times(1)).selectBatchIds(Set.of(100L, 101L));
    }

    private User user(Long id, String role) {
        User user = new User();
        user.setId(id);
        user.setUserRole(role);
        user.setIsDelete(0);
        return user;
    }

    private Project project(Long id, Long ownerId, Long teamId) {
        Project project = new Project();
        project.setId(id);
        project.setUserId(ownerId);
        project.setTeamId(teamId);
        project.setIsDelete(0);
        return project;
    }

    private Task task(Long id, Long projectId, Long assigneeId) {
        Task task = new Task();
        task.setId(id);
        task.setProjectId(projectId);
        task.setUserId(10L);
        task.setAssigneeUserId(assigneeId);
        return task;
    }

    private TeamMember member(Long teamId, Long userId, String role) {
        TeamMember member = new TeamMember();
        member.setTeamId(teamId);
        member.setUserId(userId);
        member.setRole(role);
        member.setIsDelete(0);
        return member;
    }
}
