package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.constant.PermissionActionEnum;
import com.spt.learningmanage.constant.SystemRoleEnum;
import com.spt.learningmanage.constant.TeamRoleEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.exception.PermissionDeniedException;
import com.spt.learningmanage.mapper.PermissionQueryMapper;
import com.spt.learningmanage.model.permission.ActorPermissionRow;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.model.permission.ProjectPermissionRow;
import com.spt.learningmanage.model.permission.TaskCapabilities;
import com.spt.learningmanage.model.permission.TaskPermissionRow;
import com.spt.learningmanage.model.permission.TeamMemberPermissionRow;
import com.spt.learningmanage.model.permission.WeeklyReviewPermissionRow;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.PermissionBatchPolicy;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 批量优先的资源授权判定服务。
 *
 * <p>单条入口通过单元素集合复用批量查询和内存判定内核；批量入口不逐条
 * 调用单条入口，从而保持固定的 actor + resource 查询预算。</p>
 */
@Service
public class PermissionServiceImpl implements PermissionService {

    private static final String PRIVATE_SCOPE = "PRIVATE";
    private static final String TEAM_SCOPE = "TEAM";

    @Resource
    private PermissionQueryMapper permissionQueryMapper;

    @Override
    public ProjectAccessScope requireProjectView(Long actorUserId, Long projectId) {
        ProjectAccessScope scope = requireProjectScope(actorUserId, projectId);
        if (!canViewProject(scope)) {
            throw denied();
        }
        return scope;
    }

    @Override
    public ProjectAccessScope requireProjectCreateTask(Long actorUserId, Long projectId) {
        ProjectAccessScope scope = requireProjectScope(actorUserId, projectId);
        if (!scope.canManage()) {
            throw denied();
        }
        return scope;
    }

    @Override
    public ProjectAccessScope requireProjectManage(Long actorUserId, Long projectId) {
        ProjectAccessScope scope = requireProjectScope(actorUserId, projectId);
        if (!scope.canManage()) {
            throw denied();
        }
        return scope;
    }

    @Override
    public ProjectAccessScope requireProjectMemberList(Long actorUserId, Long projectId) {
        ProjectAccessScope scope = requireProjectScope(actorUserId, projectId);
        if (!scope.isTeamProject()) {
            throw denied();
        }
        return scope;
    }

    @Override
    public ProjectAccessScope requireProjectRecover(Long actorUserId, Long projectId) {
        validateActorAndResource(actorUserId, projectId);
        requireActiveActor(actorUserId);
        ProjectPermissionRow row = single(permissionQueryMapper
                .selectProjectPermissionRows(actorUserId, List.of(projectId)));
        if (row == null || row.getProjectId() == null
                || !Objects.equals(row.getProjectId(), projectId)
                || row.getProjectDeletedAt() == null) {
            throw denied();
        }
        ProjectAccessScope scope = toProjectScope(actorUserId, row, true);
        if (scope == null || !scope.canManage()) {
            throw denied();
        }
        return scope;
    }

    @Override
    public void requireTeamView(Long actorUserId, Long teamId) {
        TeamMemberPermissionRow row = loadSingleTeamMember(actorUserId, teamId, actorUserId);
        requireActiveActorTeamRole(row);
    }

    @Override
    public void requireTeamManageProject(Long actorUserId, Long teamId) {
        TeamMemberPermissionRow row = loadSingleTeamMember(actorUserId, teamId, actorUserId);
        TeamRoleEnum role = requireActiveActorTeamRole(row);
        if (role != TeamRoleEnum.OWNER && role != TeamRoleEnum.ADMIN) {
            throw denied();
        }
    }

    @Override
    public void requireTeamMemberList(Long actorUserId, Long teamId) {
        requireTeamView(actorUserId, teamId);
    }

    @Override
    public void requireTaskView(Long actorUserId, Long taskId) {
        requireTaskAction(actorUserId, taskId, PermissionActionEnum.TASK_VIEW);
    }

    @Override
    public void requireTaskEditContent(Long actorUserId, Long taskId) {
        requireTaskAction(actorUserId, taskId, PermissionActionEnum.TASK_EDIT_CONTENT);
    }

    @Override
    public void requireTaskChangeStatus(Long actorUserId, Long taskId) {
        requireTaskAction(actorUserId, taskId, PermissionActionEnum.TASK_CHANGE_STATUS);
    }

    @Override
    public void requireTaskReorganize(Long actorUserId, Long taskId) {
        requireTaskAction(actorUserId, taskId, PermissionActionEnum.TASK_REORGANIZE);
    }

    @Override
    public void requireTaskAssign(Long actorUserId, Long taskId) {
        requireTaskAction(actorUserId, taskId, PermissionActionEnum.TASK_ASSIGN);
    }

    @Override
    public void requireTaskDelete(Long actorUserId, Long taskId) {
        requireTaskAction(actorUserId, taskId, PermissionActionEnum.TASK_DELETE);
    }

    @Override
    public void requireTaskAssignmentHistoryView(Long actorUserId, Long taskId) {
        requireTaskAction(actorUserId, taskId, PermissionActionEnum.TASK_ASSIGNMENT_HISTORY_VIEW);
    }

    @Override
    public void requireWeeklyReviewFullView(Long actorUserId, Long reviewId) {
        WeeklyReviewPermissionRow row = loadSingleReview(actorUserId, reviewId);
        if (!isStructurallyValidReview(row) || !Objects.equals(actorUserId, row.getAuthorUserId())) {
            throw denied();
        }
    }

    @Override
    public void requireWeeklyReviewUpdate(Long actorUserId, Long reviewId) {
        requireWeeklyReviewFullView(actorUserId, reviewId);
    }

    @Override
    public void requireWeeklyReviewDelete(Long actorUserId, Long reviewId) {
        requireWeeklyReviewFullView(actorUserId, reviewId);
    }

    @Override
    public void requireWeeklyReviewSharedView(Long actorUserId, Long reviewId) {
        WeeklyReviewPermissionRow row = loadSingleReview(actorUserId, reviewId);
        if (!isStructurallyValidReview(row)) {
            throw denied();
        }
        if (Objects.equals(actorUserId, row.getAuthorUserId())) {
            return;
        }
        if (!TEAM_SCOPE.equals(row.getVisibilityScope())
                || row.getTeamId() == null
                || !isActiveTeam(row.getTeamIsDelete(), row.getTeamDeletedAt())
                || !isActiveMembership(row.getActorTeamMemberId(), row.getActorMembershipIsDelete(),
                row.getActorMembershipDeletedAt())
                || activeTeamRole(row.getActorTeamMemberId(), row.getActorTeamRole(),
                row.getActorMembershipIsDelete(), row.getActorMembershipDeletedAt(),
                row.getTeamIsDelete(), row.getTeamDeletedAt(), actorUserId,
                row.getTeamOwnerUserId()) == null) {
            throw denied();
        }
    }

    @Override
    public void requireTeamMemberRoleUpdate(Long actorUserId, Long teamId, Long targetUserId) {
        TeamMemberPermissionRow row = loadSingleTeamMember(actorUserId, teamId, targetUserId);
        TeamRoleEnum actorRole = requireActiveActorTeamRole(row);
        TeamRoleEnum targetRole = requireActiveTargetTeamRole(row);
        if (actorRole != TeamRoleEnum.OWNER
                || targetRole == TeamRoleEnum.OWNER
                || Objects.equals(actorUserId, targetUserId)) {
            throw denied();
        }
    }

    @Override
    public void requireTeamMemberRemove(Long actorUserId, Long teamId, Long targetUserId) {
        TeamMemberPermissionRow row = loadSingleTeamMember(actorUserId, teamId, targetUserId);
        TeamRoleEnum actorRole = requireActiveActorTeamRole(row);
        TeamRoleEnum targetRole = requireActiveTargetTeamRole(row);
        if (Objects.equals(actorUserId, targetUserId)
                || targetRole == TeamRoleEnum.OWNER
                || (actorRole != TeamRoleEnum.OWNER && actorRole != TeamRoleEnum.ADMIN)
                || (actorRole == TeamRoleEnum.ADMIN && targetRole != TeamRoleEnum.MEMBER)) {
            throw denied();
        }
    }

    @Override
    public void requireTeamLeave(Long actorUserId, Long teamId) {
        TeamMemberPermissionRow row = loadSingleTeamMember(actorUserId, teamId, actorUserId);
        TeamRoleEnum actorRole = requireActiveActorTeamRole(row);
        if (actorRole == TeamRoleEnum.OWNER) {
            throw denied();
        }
    }

    @Override
    public Map<Long, ProjectAccessScope> resolveProjectScopes(
            Long actorUserId,
            Collection<Long> projectIds
    ) {
        List<Long> normalizedIds = normalizeBatchIds(actorUserId, projectIds);
        requireActiveActor(actorUserId);
        if (normalizedIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, ProjectPermissionRow> rows = indexProjectRows(
                permissionQueryMapper.selectProjectPermissionRows(actorUserId, normalizedIds),
                normalizedIds
        );
        Map<Long, ProjectAccessScope> result = new LinkedHashMap<>();
        for (Long projectId : normalizedIds) {
            ProjectAccessScope scope = toProjectScope(actorUserId, rows.get(projectId));
            if (scope != null) {
                result.put(projectId, scope);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public Set<Long> filterReadableTaskIds(Long actorUserId, Collection<Long> taskIds) {
        Map<Long, TaskPermissionDecision> decisions = resolveTaskDecisions(actorUserId, taskIds);
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        decisions.forEach((taskId, decision) -> {
            if (decision.readable()) {
                result.add(taskId);
            }
        });
        return Collections.unmodifiableSet(result);
    }

    @Override
    public Set<Long> filterReadableWeeklyReviewIds(Long actorUserId, Collection<Long> reviewIds) {
        List<Long> normalizedIds = normalizeBatchIds(actorUserId, reviewIds);
        requireActiveActor(actorUserId);
        if (normalizedIds.isEmpty()) {
            return Collections.emptySet();
        }
        Map<Long, WeeklyReviewPermissionRow> rows = new LinkedHashMap<>();
        for (WeeklyReviewPermissionRow row : permissionQueryMapper.selectWeeklyReviewPermissionRows(
                actorUserId, normalizedIds)) {
            if (row != null && row.getReviewId() != null && normalizedIds.contains(row.getReviewId())) {
                rows.putIfAbsent(row.getReviewId(), row);
            }
        }
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (Long reviewId : normalizedIds) {
            if (canReadWeeklyReview(actorUserId, rows.get(reviewId))) {
                result.add(reviewId);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public Set<Long> requireAllTasksReadable(Long actorUserId, Collection<Long> taskIds) {
        List<Long> normalizedIds = normalizeBatchIds(actorUserId, taskIds);
        Map<Long, TaskPermissionDecision> decisions = resolveTaskDecisions(actorUserId, normalizedIds);
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (Long taskId : normalizedIds) {
            TaskPermissionDecision decision = decisions.get(taskId);
            if (decision == null || !decision.readable()) {
                throw denied();
            }
            result.add(taskId);
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public void requireAllTasksEditableContent(Long actorUserId, Collection<Long> taskIds) {
        requireAllTaskAction(actorUserId, taskIds, PermissionActionEnum.TASK_EDIT_CONTENT);
    }

    @Override
    public void requireAllTasksReorganizable(Long actorUserId, Collection<Long> taskIds) {
        requireAllTaskAction(actorUserId, taskIds, PermissionActionEnum.TASK_REORGANIZE);
    }

    @Override
    public Map<Long, TaskCapabilities> resolveTaskCapabilities(
            Long actorUserId,
            Collection<Long> taskIds
    ) {
        Map<Long, TaskPermissionDecision> decisions = resolveTaskDecisions(actorUserId, taskIds);
        Map<Long, TaskCapabilities> result = new LinkedHashMap<>();
        decisions.forEach((taskId, decision) -> {
            if (decision.readable()) {
                result.put(taskId, decision.capabilities());
            }
        });
        return Collections.unmodifiableMap(result);
    }

    private ProjectAccessScope requireProjectScope(Long actorUserId, Long projectId) {
        validateActorAndResource(actorUserId, projectId);
        ProjectAccessScope scope = resolveProjectScopes(actorUserId, List.of(projectId)).get(projectId);
        if (scope == null) {
            throw denied();
        }
        return scope;
    }

    private ProjectAccessScope toProjectScope(Long actorUserId, ProjectPermissionRow row) {
        return toProjectScope(actorUserId, row, false);
    }

    private ProjectAccessScope toProjectScope(
            Long actorUserId,
            ProjectPermissionRow row,
            boolean allowDeletedProject
    ) {
        if (row == null || row.getProjectId() == null || row.getProjectOwnerUserId() == null
                || (!allowDeletedProject && !isActiveProject(row))
                || (allowDeletedProject && !isDeletedProject(row))) {
            return null;
        }

        if (row.getTeamId() == null) {
            if (!Objects.equals(actorUserId, row.getProjectOwnerUserId())) {
                return null;
            }
            return new ProjectAccessScope(
                    actorUserId,
                    row.getProjectId(),
                    row.getProjectOwnerUserId(),
                    null,
                    null
            );
        }

        TeamRoleEnum role = activeTeamRole(row.getActorTeamMemberId(), row.getActorTeamRole(),
                row.getActorMembershipIsDelete(), row.getActorMembershipDeletedAt(),
                row.getTeamIsDelete(), row.getTeamDeletedAt(), actorUserId, row.getTeamOwnerUserId());
        if (role == null) {
            return null;
        }
        return new ProjectAccessScope(
                actorUserId,
                row.getProjectId(),
                row.getProjectOwnerUserId(),
                row.getTeamId(),
                role
        );
    }

    private boolean canViewProject(ProjectAccessScope scope) {
        return scope.isPersonalProject() || scope.teamRole() != null;
    }

    private void requireTaskAction(Long actorUserId, Long taskId, PermissionActionEnum action) {
        validateActorAndResource(actorUserId, taskId);
        TaskPermissionDecision decision = resolveTaskDecisions(actorUserId, List.of(taskId)).get(taskId);
        if (decision == null || !decision.allowed(action)) {
            throw denied();
        }
    }

    private void requireAllTaskAction(
            Long actorUserId,
            Collection<Long> taskIds,
            PermissionActionEnum action
    ) {
        List<Long> normalizedIds = normalizeBatchIds(actorUserId, taskIds);
        Map<Long, TaskPermissionDecision> decisions = resolveTaskDecisions(actorUserId, normalizedIds);
        for (Long taskId : normalizedIds) {
            TaskPermissionDecision decision = decisions.get(taskId);
            if (decision == null || !decision.allowed(action)) {
                throw denied();
            }
        }
    }

    private Map<Long, TaskPermissionDecision> resolveTaskDecisions(
            Long actorUserId,
            Collection<Long> taskIds
    ) {
        List<Long> normalizedIds = normalizeBatchIds(actorUserId, taskIds);
        requireActiveActor(actorUserId);
        if (normalizedIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, TaskPermissionRow> rows = indexTaskRows(
                permissionQueryMapper.selectTaskPermissionRows(actorUserId, normalizedIds),
                normalizedIds
        );
        Map<Long, TaskPermissionDecision> result = new LinkedHashMap<>();
        for (Long taskId : normalizedIds) {
            result.put(taskId, evaluateTask(actorUserId, rows.get(taskId)));
        }
        return Collections.unmodifiableMap(result);
    }

    private TaskPermissionDecision evaluateTask(Long actorUserId, TaskPermissionRow row) {
        if (!isActiveTask(row)) {
            return TaskPermissionDecision.denied();
        }

        boolean personalOwner = row.getTeamId() == null
                && Objects.equals(actorUserId, row.getProjectOwnerUserId());
        if (personalOwner) {
            return TaskPermissionDecision.personalOwner();
        }

        TeamRoleEnum role = activeTeamRole(row.getActorTeamMemberId(), row.getActorTeamRole(),
                row.getActorMembershipIsDelete(), row.getActorMembershipDeletedAt(),
                row.getTeamIsDelete(), row.getTeamDeletedAt(), actorUserId, row.getTeamOwnerUserId());
        if (role == null) {
            return TaskPermissionDecision.denied();
        }

        boolean manager = role == TeamRoleEnum.OWNER || role == TeamRoleEnum.ADMIN;
        boolean assignee = role == TeamRoleEnum.MEMBER
                && Objects.equals(actorUserId, row.getAssigneeUserId());
        return new TaskPermissionDecision(
                true,
                manager || assignee,
                manager || assignee,
                manager,
                manager,
                manager,
                true,
                true
        );
    }

    private Map<Long, ProjectPermissionRow> indexProjectRows(
            List<ProjectPermissionRow> rows,
            List<Long> requestedIds
    ) {
        Map<Long, ProjectPermissionRow> indexed = new LinkedHashMap<>();
        Set<Long> requested = new LinkedHashSet<>(requestedIds);
        if (rows == null) {
            return indexed;
        }
        for (ProjectPermissionRow row : rows) {
            if (row == null || row.getProjectId() == null || !requested.contains(row.getProjectId())
                    || indexed.put(row.getProjectId(), row) != null) {
                throw denied();
            }
        }
        return indexed;
    }

    private Map<Long, TaskPermissionRow> indexTaskRows(
            List<TaskPermissionRow> rows,
            List<Long> requestedIds
    ) {
        Map<Long, TaskPermissionRow> indexed = new LinkedHashMap<>();
        Set<Long> requested = new LinkedHashSet<>(requestedIds);
        if (rows == null) {
            return indexed;
        }
        for (TaskPermissionRow row : rows) {
            if (row == null || row.getTaskId() == null || !requested.contains(row.getTaskId())
                    || indexed.put(row.getTaskId(), row) != null) {
                throw denied();
            }
        }
        return indexed;
    }

    private List<Long> normalizeBatchIds(Long actorUserId, Collection<Long> resourceIds) {
        validateActor(actorUserId);
        if (resourceIds == null || resourceIds.size() > PermissionBatchPolicy.MAX_RESOURCE_IDS) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long resourceId : resourceIds) {
            if (resourceId == null || resourceId <= 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
            }
            normalized.add(resourceId);
        }
        return List.copyOf(normalized);
    }

    private WeeklyReviewPermissionRow loadSingleReview(Long actorUserId, Long reviewId) {
        validateActorAndResource(actorUserId, reviewId);
        requireActiveActor(actorUserId);
        return single(permissionQueryMapper.selectWeeklyReviewPermissionRows(actorUserId, List.of(reviewId)));
    }

    private boolean canReadWeeklyReview(Long actorUserId, WeeklyReviewPermissionRow row) {
        if (!isStructurallyValidReview(row)) {
            return false;
        }
        if (Objects.equals(actorUserId, row.getAuthorUserId())) {
            return true;
        }
        return TEAM_SCOPE.equals(row.getVisibilityScope())
                && row.getTeamId() != null
                && isActiveTeam(row.getTeamIsDelete(), row.getTeamDeletedAt())
                && isActiveMembership(row.getActorTeamMemberId(), row.getActorMembershipIsDelete(),
                row.getActorMembershipDeletedAt())
                && activeTeamRole(row.getActorTeamMemberId(), row.getActorTeamRole(),
                row.getActorMembershipIsDelete(), row.getActorMembershipDeletedAt(),
                row.getTeamIsDelete(), row.getTeamDeletedAt(), actorUserId,
                row.getTeamOwnerUserId()) != null;
    }

    private TeamMemberPermissionRow loadSingleTeamMember(
            Long actorUserId,
            Long teamId,
            Long targetUserId
    ) {
        validateActorAndResource(actorUserId, teamId);
        validateActorAndResource(actorUserId, targetUserId);
        requireActiveActor(actorUserId);
        return permissionQueryMapper.selectTeamMemberPermissionRow(actorUserId, teamId, targetUserId);
    }

    private TeamRoleEnum requireActiveActorTeamRole(TeamMemberPermissionRow row) {
        if (row == null) {
            throw denied();
        }
        TeamRoleEnum role = activeTeamRole(
                row.getActorTeamMemberId(), row.getActorTeamRole(),
                row.getActorMembershipIsDelete(), row.getActorMembershipDeletedAt(),
                row.getTeamIsDelete(), row.getTeamDeletedAt(),
                row.getActorUserId(), row.getTeamOwnerUserId()
        );
        if (role == null) {
            throw denied();
        }
        return role;
    }

    private TeamRoleEnum requireActiveTargetTeamRole(TeamMemberPermissionRow row) {
        if (row == null) {
            throw denied();
        }
        TeamRoleEnum role = activeTeamRole(
                row.getTargetTeamMemberId(), row.getTargetTeamRole(),
                row.getTargetMembershipIsDelete(), row.getTargetMembershipDeletedAt(),
                row.getTeamIsDelete(), row.getTeamDeletedAt(),
                row.getTargetUserId(), row.getTeamOwnerUserId()
        );
        if (role == null) {
            throw denied();
        }
        return role;
    }

    private boolean isActiveProject(ProjectPermissionRow row) {
        return row != null
                && row.getProjectId() != null
                && row.getProjectOwnerUserId() != null
                && isNotDeleted(row.getProjectIsDelete(), row.getProjectDeletedAt());
    }

    private boolean isDeletedProject(ProjectPermissionRow row) {
        return row != null
                && row.getProjectId() != null
                && row.getProjectOwnerUserId() != null
                && row.getProjectIsDelete() != null
                && row.getProjectDeletedAt() != null;
    }

    private boolean isActiveTask(TaskPermissionRow row) {
        return row != null
                && row.getTaskId() != null
                && row.getProjectId() != null
                && row.getProjectOwnerUserId() != null
                && isNotDeleted(row.getTaskIsDelete(), row.getTaskDeletedAt())
                && isNotDeleted(row.getProjectIsDelete(), row.getProjectDeletedAt())
                && (row.getTeamId() == null
                || isActiveTeam(row.getTeamIsDelete(), row.getTeamDeletedAt()));
    }

    private boolean isStructurallyValidReview(WeeklyReviewPermissionRow row) {
        if (row == null || row.getReviewId() == null || row.getAuthorUserId() == null) {
            return false;
        }
        if (PRIVATE_SCOPE.equals(row.getVisibilityScope())) {
            return row.getTeamId() == null;
        }
        return TEAM_SCOPE.equals(row.getVisibilityScope()) && row.getTeamId() != null;
    }

    private boolean isNotDeleted(Integer isDelete, Object deletedAt) {
        return Objects.equals(isDelete, 0) && deletedAt == null;
    }

    private boolean isActiveTeam(Integer isDelete, Object deletedAt) {
        return isNotDeleted(isDelete, deletedAt);
    }

    private boolean isActiveMembership(
            Long memberId,
            Integer isDelete,
            Object deletedAt
    ) {
        return memberId != null && Objects.equals(isDelete, 0) && deletedAt == null;
    }

    private TeamRoleEnum activeTeamRole(
            Long memberId,
            String roleValue,
            Integer membershipIsDelete,
            Object membershipDeletedAt,
            Integer teamIsDelete,
            Object teamDeletedAt,
            Long subjectUserId,
            Long teamOwnerUserId
    ) {
        if (!isActiveTeam(teamIsDelete, teamDeletedAt)
                || !isActiveMembership(memberId, membershipIsDelete, membershipDeletedAt)
                || subjectUserId == null
                || teamOwnerUserId == null) {
            return null;
        }
        TeamRoleEnum role = parseTeamRole(roleValue);
        if (role == null) {
            return null;
        }
        boolean ownerById = Objects.equals(subjectUserId, teamOwnerUserId);
        boolean ownerByRole = role == TeamRoleEnum.OWNER;
        return ownerById == ownerByRole ? role : null;
    }

    private TeamRoleEnum parseTeamRole(String roleValue) {
        return TeamRoleEnum.fromValue(roleValue);
    }

    private <T> T single(List<T> rows) {
        if (rows == null || rows.size() != 1) {
            throw denied();
        }
        return rows.get(0);
    }

    private void validateActorAndResource(Long actorUserId, Long resourceId) {
        validateActor(actorUserId);
        if (resourceId == null || resourceId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
    }

    private void validateActor(Long actorUserId) {
        if (actorUserId == null || actorUserId <= 0) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
    }

    @Override
    public void requireActiveActor(Long actorUserId) {
        ActorPermissionRow row = permissionQueryMapper.selectActorPermissionRow(actorUserId);
        if (row == null
                || !Objects.equals(row.getActorUserId(), actorUserId)
                || !Objects.equals(row.getActorIsDelete(), 0)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "登录状态已失效，请重新登录");
        }
        SystemRoleEnum systemRole = SystemRoleEnum.fromValue(row.getActorSystemRole());
        if (systemRole == null) {
            throw denied();
        }
    }

    @Override
    public void requireSystemAdmin(Long actorUserId) {
        ActorPermissionRow row = permissionQueryMapper.selectActorPermissionRow(actorUserId);
        if (row == null
                || !Objects.equals(row.getActorUserId(), actorUserId)
                || !Objects.equals(row.getActorIsDelete(), 0)
                || !SystemRoleEnum.isSystemAdmin(row.getActorSystemRole())) {
            throw denied();
        }
    }

    private record TaskPermissionDecision(
            boolean readable,
            boolean canEditContent,
            boolean canChangeStatus,
            boolean canReorganize,
            boolean canAssign,
            boolean canDelete,
            boolean canViewAssignmentHistory,
            boolean canView
    ) {

        private static TaskPermissionDecision denied() {
            return new TaskPermissionDecision(false, false, false, false, false, false, false, false);
        }

        private static TaskPermissionDecision personalOwner() {
            return new TaskPermissionDecision(true, true, true, true, true, true, true, true);
        }

        private boolean allowed(PermissionActionEnum action) {
            return switch (action.canonical()) {
                case TASK_VIEW -> canView;
                case TASK_EDIT_CONTENT -> canEditContent;
                case TASK_CHANGE_STATUS -> canChangeStatus;
                case TASK_REORGANIZE -> canReorganize;
                case TASK_ASSIGN -> canAssign;
                case TASK_DELETE -> canDelete;
                case TASK_ASSIGNMENT_HISTORY_VIEW -> canViewAssignmentHistory;
                default -> false;
            };
        }

        private TaskCapabilities capabilities() {
            return new TaskCapabilities(
                    canEditContent,
                    canChangeStatus,
                    canReorganize,
                    canAssign,
                    canDelete
            );
        }
    }

    private PermissionDeniedException denied() {
        return new PermissionDeniedException();
    }
}
