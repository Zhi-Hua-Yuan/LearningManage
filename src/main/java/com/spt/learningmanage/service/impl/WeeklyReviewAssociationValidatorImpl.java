package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.constant.WeeklyReviewVisibilityScopeEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.exception.PermissionDeniedException;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.model.review.WeeklyReviewAssociationContext;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.WeeklyReviewAssociationValidator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Batch-first validation for weekly-review project/task associations.
 * Missing, deleted, unauthorized, cross-team and malformed resources reject
 * the complete write request.
 */
@Service
public class WeeklyReviewAssociationValidatorImpl implements WeeklyReviewAssociationValidator {

    private static final int MAX_ASSOCIATIONS = 500;

    @Resource
    private PermissionService permissionService;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Override
    public WeeklyReviewAssociationContext validate(
            Long actorUserId,
            WeeklyReviewVisibilityScopeEnum scope,
            Long teamId,
            Long focusProjectId,
            Collection<Long> taskIds
    ) {
        if (actorUserId == null || actorUserId <= 0 || scope == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "周总结关联参数不合法");
        }
        if (scope == WeeklyReviewVisibilityScopeEnum.TEAM
                && (teamId == null || teamId <= 0)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "TEAM 周总结必须指定团队");
        }
        if (focusProjectId != null && focusProjectId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "focusProjectId 不合法");
        }

        List<Long> normalizedTaskIds = normalizeTaskIds(taskIds);
        Set<Long> readableTaskIds = normalizedTaskIds.isEmpty()
                ? Set.of()
                : permissionService.requireAllTasksReadable(actorUserId, normalizedTaskIds);
        if (!normalizedTaskIds.isEmpty()
                && (readableTaskIds == null
                || !readableTaskIds.equals(new LinkedHashSet<>(normalizedTaskIds)))) {
            throw denied();
        }

        Map<Long, Task> tasks = loadTasks(normalizedTaskIds);
        LinkedHashSet<Long> projectIds = new LinkedHashSet<>();
        if (focusProjectId != null) {
            projectIds.add(focusProjectId);
        }
        for (Long taskId : normalizedTaskIds) {
            Task task = tasks.get(taskId);
            if (task == null || task.getProjectId() == null) {
                throw denied();
            }
            projectIds.add(task.getProjectId());
        }

        List<Long> normalizedProjectIds = List.copyOf(projectIds);
        Map<Long, ProjectAccessScope> scopes = normalizedProjectIds.isEmpty()
                ? Collections.emptyMap()
                : permissionService.resolveProjectScopes(actorUserId, normalizedProjectIds);
        for (Long projectId : normalizedProjectIds) {
            ProjectAccessScope projectScope = scopes.get(projectId);
            if (projectScope == null) {
                throw denied();
            }
            if (scope == WeeklyReviewVisibilityScopeEnum.TEAM
                    && (!projectScope.isTeamProject() || !teamId.equals(projectScope.teamId()))) {
                throw denied();
            }
        }

        String focusProjectName = null;
        if (focusProjectId != null) {
            Project focusProject = loadProjects(normalizedProjectIds).get(focusProjectId);
            if (focusProject == null || focusProject.getName() == null) {
                throw denied();
            }
            focusProjectName = focusProject.getName();
        }
        return new WeeklyReviewAssociationContext(focusProjectId, focusProjectName, normalizedTaskIds);
    }

    private List<Long> normalizeTaskIds(Collection<Long> taskIds) {
        if (taskIds == null) {
            return List.of();
        }
        if (taskIds.size() > MAX_ASSOCIATIONS) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "taskIds 数量超出限制");
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long taskId : taskIds) {
            if (taskId == null || taskId <= 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "taskIds 不合法");
            }
            normalized.add(taskId);
        }
        return List.copyOf(normalized);
    }

    private Map<Long, Task> loadTasks(Collection<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Task> rows = taskMapper.selectBatchIds(taskIds);
        Map<Long, Task> result = new LinkedHashMap<>();
        if (rows != null) {
            for (Task task : rows) {
                if (task == null || task.getId() == null || result.put(task.getId(), task) != null) {
                    throw denied();
                }
            }
        }
        if (result.size() != taskIds.size() || !result.keySet().containsAll(taskIds)) {
            throw denied();
        }
        return result;
    }

    private Map<Long, Project> loadProjects(Collection<Long> projectIds) {
        List<Project> rows = projectMapper.selectBatchIds(projectIds);
        Map<Long, Project> result = new LinkedHashMap<>();
        if (rows != null) {
            for (Project project : rows) {
                if (project == null || project.getId() == null || result.put(project.getId(), project) != null) {
                    throw denied();
                }
            }
        }
        return result;
    }

    private PermissionDeniedException denied() {
        return new PermissionDeniedException();
    }
}
