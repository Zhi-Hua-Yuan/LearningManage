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
import com.spt.learningmanage.model.permission.TaskPermissionRow;
import com.spt.learningmanage.model.permission.TeamMemberPermissionRow;
import com.spt.learningmanage.model.permission.WeeklyReviewPermissionRow;
import com.spt.learningmanage.service.PermissionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 单资源授权判定骨架。
 *
 * <p>所有查询都通过批量优先的 PermissionQueryMapper 执行 singleton 查询；
 * 后续批量授权可复用同一组 SQL 和规则，不会产生第二套判断路径。</p>
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

    private ProjectAccessScope requireProjectScope(Long actorUserId, Long projectId) {
        validateActorAndResource(actorUserId, projectId);
        requireActiveActor(actorUserId);
        ProjectPermissionRow row = single(
                permissionQueryMapper.selectProjectPermissionRows(actorUserId, List.of(projectId))
        );
        if (!isActiveProject(row)) {
            throw denied();
        }

        if (row.getTeamId() == null) {
            if (!Objects.equals(actorUserId, row.getProjectOwnerUserId())) {
                throw denied();
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
            throw denied();
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
        requireActiveActor(actorUserId);
        TaskPermissionRow row = single(
                permissionQueryMapper.selectTaskPermissionRows(actorUserId, List.of(taskId))
        );
        if (!isActiveTask(row)) {
            throw denied();
        }

        boolean personalOwner = row.getTeamId() == null
                && Objects.equals(actorUserId, row.getProjectOwnerUserId());
        if (personalOwner) {
            return;
        }

        TeamRoleEnum role = activeTeamRole(row.getActorTeamMemberId(), row.getActorTeamRole(),
                row.getActorMembershipIsDelete(), row.getActorMembershipDeletedAt(),
                row.getTeamIsDelete(), row.getTeamDeletedAt(), actorUserId, row.getTeamOwnerUserId());
        if (role == null) {
            throw denied();
        }

        boolean manager = role == TeamRoleEnum.OWNER || role == TeamRoleEnum.ADMIN;
        boolean assignee = role == TeamRoleEnum.MEMBER
                && Objects.equals(actorUserId, row.getAssigneeUserId());
        boolean allowed = switch (action.canonical()) {
            case TASK_VIEW, TASK_ASSIGNMENT_HISTORY_VIEW -> true;
            case TASK_EDIT_CONTENT, TASK_CHANGE_STATUS -> manager || assignee;
            case TASK_REORGANIZE, TASK_ASSIGN, TASK_DELETE -> manager;
            default -> false;
        };
        if (!allowed) {
            throw denied();
        }
    }

    private WeeklyReviewPermissionRow loadSingleReview(Long actorUserId, Long reviewId) {
        validateActorAndResource(actorUserId, reviewId);
        requireActiveActor(actorUserId);
        return single(permissionQueryMapper.selectWeeklyReviewPermissionRows(actorUserId, List.of(reviewId)));
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
        if (actorUserId == null || actorUserId <= 0) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (resourceId == null || resourceId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
    }

    private ActiveActor requireActiveActor(Long actorUserId) {
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
        return new ActiveActor(actorUserId, systemRole);
    }

    private record ActiveActor(Long userId, SystemRoleEnum systemRole) {
    }

    private PermissionDeniedException denied() {
        return new PermissionDeniedException();
    }
}
