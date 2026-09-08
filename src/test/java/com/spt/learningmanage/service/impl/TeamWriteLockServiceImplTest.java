package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.constant.TeamRoleEnum;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TeamMapper;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Team;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamWriteLockServiceImplTest {

    @Mock private TeamMapper teamMapper;
    @Mock private ProjectMapper projectMapper;

    @Test
    void teamProjectLocksTeamBeforeProject() {
        TeamWriteLockServiceImpl service = new TeamWriteLockServiceImpl(teamMapper, projectMapper);
        ProjectAccessScope scope = new ProjectAccessScope(1L, 10L, 1L, 20L, TeamRoleEnum.OWNER);
        Team team = new Team();
        team.setId(20L);
        Project project = project(10L, 1L, 20L);
        when(teamMapper.selectActiveByIdForUpdate(20L)).thenReturn(team);
        when(projectMapper.selectActiveByIdForUpdate(10L)).thenReturn(project);

        service.lockProjectScope(scope);

        InOrder order = inOrder(teamMapper, projectMapper);
        order.verify(teamMapper).selectActiveByIdForUpdate(20L);
        order.verify(projectMapper).selectActiveByIdForUpdate(10L);
    }

    @Test
    void personalProjectLocksOnlyProject() {
        TeamWriteLockServiceImpl service = new TeamWriteLockServiceImpl(teamMapper, projectMapper);
        ProjectAccessScope scope = new ProjectAccessScope(1L, 10L, 1L, null, null);
        when(projectMapper.selectActiveByIdForUpdate(10L)).thenReturn(project(10L, 1L, null));

        service.lockProjectScope(scope);

        verify(teamMapper, never()).selectActiveByIdForUpdate(org.mockito.ArgumentMatchers.any());
        verify(projectMapper).selectActiveByIdForUpdate(10L);
    }

    private Project project(Long id, Long ownerId, Long teamId) {
        Project project = new Project();
        project.setId(id);
        project.setUserId(ownerId);
        project.setTeamId(teamId);
        return project;
    }
}
