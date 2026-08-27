package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskAssignmentLogMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.TeamMapper;
import com.spt.learningmanage.mapper.TeamMemberMapper;
import com.spt.learningmanage.mapper.UserMapper;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.TaskAssignmentLog;
import com.spt.learningmanage.model.entity.Team;
import com.spt.learningmanage.model.entity.TeamMember;
import com.spt.learningmanage.model.entity.User;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.utils.UserHolder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, Team.class);
        TableInfoHelper.initTableInfo(assistant, TeamMember.class);
        TableInfoHelper.initTableInfo(assistant, Project.class);
        TableInfoHelper.initTableInfo(assistant, Task.class);
    }

    @Mock private TeamMapper teamMapper;
    @Mock private TeamMemberMapper teamMemberMapper;
    @Mock private UserMapper userMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private TaskAssignmentLogMapper taskAssignmentLogMapper;
    @Mock private PermissionService permissionService;

    @InjectMocks
    private TeamServiceImpl teamService;

    @AfterEach
    void tearDown() {
        UserHolder.remove();
    }

    @Test
    void leaveTeam_shouldUnassignUnfinishedTasksAndDeactivateMember() {
        UserHolder.set(12L);
        when(teamMapper.selectOne(any())).thenReturn(team(31L));
        when(teamMemberMapper.selectOne(any())).thenReturn(member(31L, 12L, "MEMBER"),
                member(31L, 12L, "MEMBER"));
        Task task = task(101L, 41L, 12L, 0);
        when(projectMapper.selectList(any())).thenReturn(List.of(project(41L, 31L)));
        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        when(taskMapper.update(any(), any())).thenReturn(1);
        when(taskAssignmentLogMapper.insert(any(TaskAssignmentLog.class))).thenReturn(1);
        when(teamMemberMapper.deactivateMember(any(), any(), any())).thenReturn(1);

        teamService.leaveTeam(31L);

        verify(taskMapper).update(any(), any());
        verify(taskAssignmentLogMapper).insert(any(TaskAssignmentLog.class));
        verify(teamMemberMapper).deactivateMember(eq(31L), eq(12L), any());
    }

    @Test
    void leaveTeam_shouldRejectOwner() {
        UserHolder.set(11L);
        when(teamMapper.selectOne(any())).thenReturn(team(31L));
        when(teamMemberMapper.selectOne(any())).thenReturn(member(31L, 11L, "OWNER"));

        BusinessException exception = assertThrows(BusinessException.class, () -> teamService.leaveTeam(31L));

        assertEquals(ErrorCode.NO_AUTH_ERROR, exception.getErrorCode());
        verify(projectMapper, never()).selectList(any());
        verify(teamMemberMapper, never()).deactivateMember(any(), any(), any());
    }

    @Test
    void removeMember_adminCanRemoveMemberAndUnassignTasks() {
        UserHolder.set(12L);
        when(teamMapper.selectOne(any())).thenReturn(team(31L));
        when(teamMemberMapper.selectOne(any())).thenReturn(member(31L, 12L, "ADMIN"),
                member(31L, 13L, "MEMBER"));
        when(projectMapper.selectList(any())).thenReturn(List.of(project(41L, 31L)));
        when(taskMapper.selectList(any())).thenReturn(List.of(task(101L, 41L, 13L, 0)));
        when(taskMapper.update(any(), any())).thenReturn(1);
        when(taskAssignmentLogMapper.insert(any(TaskAssignmentLog.class))).thenReturn(1);
        when(teamMemberMapper.deactivateMember(any(), any(), any())).thenReturn(1);

        teamService.removeMember(31L, 13L);

        verify(taskAssignmentLogMapper).insert(any(TaskAssignmentLog.class));
        verify(teamMemberMapper).deactivateMember(eq(31L), eq(13L), any());
    }

    @Test
    void removeMember_adminCannotRemoveAdmin() {
        UserHolder.set(12L);
        when(teamMapper.selectOne(any())).thenReturn(team(31L));
        when(teamMemberMapper.selectOne(any())).thenReturn(member(31L, 12L, "ADMIN"),
                member(31L, 13L, "ADMIN"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> teamService.removeMember(31L, 13L));

        assertEquals(ErrorCode.NO_AUTH_ERROR, exception.getErrorCode());
        verify(projectMapper, never()).selectList(any());
        verify(teamMemberMapper, never()).deactivateMember(any(), any(), any());
    }

    @Test
    void removeMember_shouldStopBeforeMemberDeactivationWhenTaskUpdateFails() {
        UserHolder.set(11L);
        when(teamMapper.selectOne(any())).thenReturn(team(31L));
        when(teamMemberMapper.selectOne(any())).thenReturn(member(31L, 11L, "OWNER"),
                member(31L, 13L, "MEMBER"));
        when(projectMapper.selectList(any())).thenReturn(List.of(project(41L, 31L)));
        when(taskMapper.selectList(any())).thenReturn(List.of(task(101L, 41L, 13L, 0)));
        when(taskMapper.update(any(), any())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> teamService.removeMember(31L, 13L));

        assertEquals(ErrorCode.OPERATION_ERROR, exception.getErrorCode());
        verify(taskAssignmentLogMapper, never()).insert(any(TaskAssignmentLog.class));
        verify(teamMemberMapper, never()).deactivateMember(any(), any(), any());
    }

    private Team team(Long id) {
        Team team = new Team();
        team.setId(id);
        team.setIsDelete(0);
        return team;
    }

    private TeamMember member(Long teamId, Long userId, String role) {
        TeamMember member = new TeamMember();
        member.setTeamId(teamId);
        member.setUserId(userId);
        member.setRole(role);
        member.setIsDelete(0);
        return member;
    }

    private Project project(Long id, Long teamId) {
        Project project = new Project();
        project.setId(id);
        project.setTeamId(teamId);
        project.setIsDelete(0);
        return project;
    }

    private Task task(Long id, Long projectId, Long assigneeId, int status) {
        Task task = new Task();
        task.setId(id);
        task.setProjectId(projectId);
        task.setAssigneeUserId(assigneeId);
        task.setStatus(status);
        task.setIsDelete(0);
        return task;
    }
}
