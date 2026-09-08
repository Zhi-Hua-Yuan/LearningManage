package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TeamMapper;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.service.TeamWriteLockService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Service
public class TeamWriteLockServiceImpl implements TeamWriteLockService {

    private final TeamMapper teamMapper;
    private final ProjectMapper projectMapper;

    public TeamWriteLockServiceImpl(TeamMapper teamMapper, ProjectMapper projectMapper) {
        this.teamMapper = teamMapper;
        this.projectMapper = projectMapper;
    }

    @Override
    public void lockTeam(Long teamId) {
        if (teamId == null) {
            return;
        }
        if (teamId <= 0 || teamMapper.selectActiveByIdForUpdate(teamId) == null) {
            throw new BusinessException(ErrorCode.TEAM_STATE_CONFLICT,
                    "团队状态已发生变化，请刷新后重试");
        }
    }

    @Override
    public void lockProjectScope(ProjectAccessScope scope) {
        if (scope == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目权限范围不能为空");
        }
        lockTeam(scope.teamId());
        Project locked = projectMapper.selectActiveByIdForUpdate(scope.projectId());
        if (locked == null
                || !Objects.equals(locked.getUserId(), scope.projectOwnerUserId())
                || !Objects.equals(locked.getTeamId(), scope.teamId())) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
    }

    @Override
    public void lockOwningTeam(Long projectId) {
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目 ID 不合法");
        }
        Project observed = projectMapper.selectById(projectId);
        if (observed == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        lockTeam(observed.getTeamId());
        Project locked = projectMapper.selectActiveByIdForUpdate(projectId);
        if (locked == null || !Objects.equals(observed.getTeamId(), locked.getTeamId())) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
    }

    @Override
    public void lockOwningTeams(Collection<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return;
        }
        List<Long> normalizedProjectIds = projectIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .sorted()
                .toList();
        if (normalizedProjectIds.isEmpty()) {
            return;
        }
        List<Project> projects = projectMapper.selectBatchIds(normalizedProjectIds);
        if (projects.size() != normalizedProjectIds.size()) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        projects.stream()
                .map(Project::getTeamId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(this::lockTeam);
        projects.stream()
                .sorted(java.util.Comparator.comparing(Project::getId))
                .forEach(project -> {
                    Project locked = projectMapper.selectActiveByIdForUpdate(project.getId());
                    if (locked == null || !Objects.equals(project.getTeamId(), locked.getTeamId())) {
                        throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
                    }
                });
    }
}
