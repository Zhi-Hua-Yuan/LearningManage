package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spt.learningmanage.constant.SystemRole;
import com.spt.learningmanage.constant.TeamRoleEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
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
import com.spt.learningmanage.service.PermissionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PermissionServiceImpl implements PermissionService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private TeamMemberMapper teamMemberMapper;

    @Resource
    private WeeklyReviewMapper weeklyReviewMapper;

    @Override
    public ProjectAccessScope requireProjectView(Long actorId, Long projectId) {
        ProjectAccessScope scope = resolveProjectScope(actorId, projectId);
        if (!scope.canView()) {
            throw denied();
        }
        return scope;
    }

    @Override
    public ProjectAccessScope requireProjectManage(Long actorId, Long projectId) {
        ProjectAccessScope scope = resolveProjectScope(actorId, projectId);
        if (!scope.canManage()) {
            throw denied();
        }
        return scope;
    }

    @Override
    public void requireTaskView(Long actorId, Long taskId) {
        TaskContext context = loadTaskContext(actorId, taskId);
        if (!context.scope().canView()) {
            throw denied();
        }
    }

    @Override
    public void requireTaskEditContent(Long actorId, Long taskId) {
        requireTaskCapability(actorId, taskId, Capability.EDIT_CONTENT);
    }

    @Override
    public void requireTaskChangeStatus(Long actorId, Long taskId) {
        requireTaskCapability(actorId, taskId, Capability.CHANGE_STATUS);
    }

    @Override
    public void requireTaskReorganize(Long actorId, Long taskId) {
        requireTaskCapability(actorId, taskId, Capability.REORGANIZE);
    }

    @Override
    public void requireTaskAssign(Long actorId, Long taskId) {
        requireTaskCapability(actorId, taskId, Capability.MANAGE);
    }

    @Override
    public void requireTaskDelete(Long actorId, Long taskId) {
        requireTaskCapability(actorId, taskId, Capability.MANAGE);
    }

    @Override
    public void requireWeeklyReviewFullView(Long actorId, Long reviewId) {
        WeeklyReview review = loadReview(reviewId);
        if (!sameId(actorId, review.getUserId())) {
            throw denied();
        }
    }

    @Override
    public void requireWeeklyReviewSharedView(Long actorId, Long reviewId) {
        WeeklyReview review = loadReview(reviewId);
        if (!"TEAM".equals(review.getVisibilityScope()) || review.getTeamId() == null) {
            throw denied();
        }
        requireActiveTeamMemberInternal(actorId, review.getTeamId());
    }

    @Override
    public void requireActiveTeamMember(Long actorId, Long teamId) {
        if (teamId == null || teamId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "团队 ID 不合法");
        }
        requireActiveTeamMemberInternal(actorId, teamId);
    }

    @Override
    public Map<Long, ProjectAccessScope> resolveProjectScopes(Long actorId, Collection<Long> projectIds) {
        requireActor(actorId);
        if (projectIds == null || projectIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> ids = projectIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Project> projects = projectMapper.selectBatchIds(ids);
        if (projects == null || projects.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> teamIds = projects.stream()
                .map(Project::getTeamId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, TeamRoleEnum> actorTeamRoles = new HashMap<>();
        if (!teamIds.isEmpty()) {
            List<TeamMember> memberships = teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                    .in(TeamMember::getTeamId, teamIds)
                    .eq(TeamMember::getUserId, actorId)
                    .eq(TeamMember::getIsDelete, 0));
            if (memberships != null) {
                for (TeamMember membership : memberships) {
                    TeamRoleEnum role = TeamRoleEnum.fromValue(membership.getRole());
                    if (role != null) {
                        actorTeamRoles.put(membership.getTeamId(), role);
                    }
                }
            }
        }

        Map<Long, ProjectAccessScope> result = new HashMap<>();
        for (Project project : projects) {
            boolean owner = sameId(actorId, project.getUserId());
            TeamRoleEnum teamRole = project.getTeamId() == null
                    ? null : actorTeamRoles.get(project.getTeamId());
            boolean member = teamRole != null;
            boolean canView = project.getTeamId() == null ? owner : member;
            boolean canManage = project.getTeamId() == null
                    ? owner
                    : teamRole == TeamRoleEnum.OWNER || teamRole == TeamRoleEnum.ADMIN;
            result.put(project.getId(), new ProjectAccessScope(
                    project.getId(), project.getUserId(), project.getTeamId(), teamRole,
                    owner, canView, canManage));
        }
        return result;
    }

    @Override
    public Set<Long> filterReadableTaskIds(Long actorId, Collection<Long> taskIds) {
        requireActor(actorId);
        if (taskIds == null || taskIds.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> ids = taskIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptySet();
        }

        List<Task> tasks = taskMapper.selectBatchIds(ids);
        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptySet();
        }
        Map<Long, ProjectAccessScope> scopes = resolveProjectScopes(actorId,
                tasks.stream().map(Task::getProjectId).collect(Collectors.toSet()));
        Set<Long> readable = new HashSet<>();
        for (Task task : tasks) {
            ProjectAccessScope scope = scopes.get(task.getProjectId());
            if (scope != null && scope.canView()) {
                readable.add(task.getId());
            }
        }
        return readable;
    }

    private void requireTaskCapability(Long actorId, Long taskId, Capability capability) {
        TaskContext context = loadTaskContext(actorId, taskId);
        ProjectAccessScope scope = context.scope();
        if (!scope.canView()) {
            throw denied();
        }
        boolean manager = scope.canManage();
        boolean assignee = sameId(actorId, context.task().getAssigneeUserId());
        if (capability == Capability.EDIT_CONTENT || capability == Capability.CHANGE_STATUS) {
            if (!manager && !assignee) {
                throw denied();
            }
            return;
        }
        if (!manager) {
            throw denied();
        }
    }

    private ProjectAccessScope resolveProjectScope(Long actorId, Long projectId) {
        requireActor(actorId);
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目 ID 不合法");
        }
        Map<Long, ProjectAccessScope> scopes = resolveProjectScopes(actorId, List.of(projectId));
        ProjectAccessScope scope = scopes.get(projectId);
        if (scope == null) {
            throw denied();
        }
        return scope;
    }

    private TaskContext loadTaskContext(Long actorId, Long taskId) {
        requireActor(actorId);
        if (taskId == null || taskId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务 ID 不合法");
        }
        Task task = taskMapper.selectById(taskId);
        if (task == null || task.getProjectId() == null) {
            throw denied();
        }
        ProjectAccessScope scope = resolveProjectScope(actorId, task.getProjectId());
        return new TaskContext(task, scope);
    }

    private WeeklyReview loadReview(Long reviewId) {
        if (reviewId == null || reviewId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "复盘 ID 不合法");
        }
        WeeklyReview review = weeklyReviewMapper.selectById(reviewId);
        if (review == null) {
            throw denied();
        }
        return review;
    }

    private void requireActiveTeamMemberInternal(Long actorId, Long teamId) {
        requireActor(actorId);
        TeamMember membership = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, actorId)
                .eq(TeamMember::getIsDelete, 0)
                .last("limit 1"));
        if (membership == null || TeamRoleEnum.fromValue(membership.getRole()) == null) {
            throw denied();
        }
    }

    private User requireActor(Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        User user = userMapper.selectById(actorId);
        if (user == null || user.getIsDelete() != null && user.getIsDelete() != 0) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (SystemRole.fromValue(user.getUserRole()) == null) {
            throw denied();
        }
        return user;
    }

    private PermissionDeniedException denied() {
        return new PermissionDeniedException();
    }

    private boolean sameId(Long left, Long right) {
        return left != null && left.equals(right);
    }

    private enum Capability {
        EDIT_CONTENT,
        CHANGE_STATUS,
        REORGANIZE,
        MANAGE
    }

    private record TaskContext(Task task, ProjectAccessScope scope) {
    }
}
