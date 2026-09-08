package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.constant.TeamRoleEnum;
import com.spt.learningmanage.exception.PermissionDeniedException;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TeamMemberMapper;
import com.spt.learningmanage.mapper.TeamMapper;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Team;
import com.spt.learningmanage.model.entity.TeamMember;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamWriteLockServiceImplTest {

    @Mock private TeamMapper teamMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private TeamMemberMapper teamMemberMapper;

    @Test
    void teamProjectLocksTeamBeforeProject() {
        TeamWriteLockServiceImpl service = new TeamWriteLockServiceImpl(
                teamMapper, projectMapper, teamMemberMapper);
        ProjectAccessScope scope = new ProjectAccessScope(1L, 10L, 1L, 20L, TeamRoleEnum.OWNER);
        Team team = new Team();
        team.setId(20L);
        Project project = project(10L, 1L, 20L);
        when(teamMapper.selectActiveByIdForUpdate(20L)).thenReturn(team);
        when(projectMapper.selectActiveByIdForUpdate(10L)).thenReturn(project);
        when(teamMemberMapper.selectActiveMembersForUpdate(20L, List.of(1L)))
                .thenReturn(List.of(membership(1L, TeamRoleEnum.OWNER)));

        service.lockProjectScope(scope);

        InOrder order = inOrder(teamMapper, projectMapper, teamMemberMapper);
        order.verify(teamMapper).selectActiveByIdForUpdate(20L);
        order.verify(projectMapper).selectActiveByIdForUpdate(10L);
        order.verify(teamMemberMapper).selectActiveMembersForUpdate(20L, List.of(1L));
    }

    @Test
    void personalProjectLocksOnlyProject() {
        TeamWriteLockServiceImpl service = new TeamWriteLockServiceImpl(
                teamMapper, projectMapper, teamMemberMapper);
        ProjectAccessScope scope = new ProjectAccessScope(1L, 10L, 1L, null, null);
        when(projectMapper.selectActiveByIdForUpdate(10L)).thenReturn(project(10L, 1L, null));

        service.lockProjectScope(scope);

        verify(teamMapper, never()).selectActiveByIdForUpdate(org.mockito.ArgumentMatchers.any());
        verify(teamMemberMapper, never()).selectActiveMembersForUpdate(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(projectMapper).selectActiveByIdForUpdate(10L);
    }

    @Test
    void teamProjectRejectsRoleRevokedBeforeCurrentRead() {
        TeamWriteLockServiceImpl service = new TeamWriteLockServiceImpl(
                teamMapper, projectMapper, teamMemberMapper);
        ProjectAccessScope staleScope = new ProjectAccessScope(
                1L, 10L, 1L, 20L, TeamRoleEnum.ADMIN);
        Team team = new Team();
        team.setId(20L);
        when(teamMapper.selectActiveByIdForUpdate(20L)).thenReturn(team);
        when(projectMapper.selectActiveByIdForUpdate(10L)).thenReturn(project(10L, 1L, 20L));
        when(teamMemberMapper.selectActiveMembersForUpdate(20L, List.of(1L)))
                .thenReturn(List.of(membership(1L, TeamRoleEnum.MEMBER)));

        assertThrows(PermissionDeniedException.class,
                () -> service.lockProjectScope(staleScope));
    }

    private Project project(Long id, Long ownerId, Long teamId) {
        Project project = new Project();
        project.setId(id);
        project.setUserId(ownerId);
        project.setTeamId(teamId);
        return project;
    }

    private TeamMember membership(Long userId, TeamRoleEnum role) {
        TeamMember membership = new TeamMember();
        membership.setUserId(userId);
        membership.setRole(role.getValue());
        return membership;
    }
}
