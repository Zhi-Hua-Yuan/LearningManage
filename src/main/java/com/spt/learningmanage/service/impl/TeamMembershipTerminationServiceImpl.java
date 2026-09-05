package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spt.learningmanage.constant.KnowledgeEventTypeEnum;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.constant.TaskAssignmentActionEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.exception.PermissionDeniedException;
import com.spt.learningmanage.mapper.TaskAssignmentLogMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.TeamMemberMapper;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.WeeklyReviewMapper;
import com.spt.learningmanage.model.dto.team.TeamMemberRemoveRequest;
import com.spt.learningmanage.model.entity.TaskAssignmentLog;
import com.spt.learningmanage.model.entity.TeamMember;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.model.query.team.MembershipTaskCleanupRow;
import com.spt.learningmanage.model.vo.team.TeamMembershipTerminationVO;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.KnowledgeIndexEventPublisher;
import com.spt.learningmanage.service.TeamMembershipTerminationPolicy;
import com.spt.learningmanage.service.TeamMembershipTerminationService;
import com.spt.learningmanage.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 团队成员终止事务实现。 */
@Service
public class TeamMembershipTerminationServiceImpl
        implements TeamMembershipTerminationService {

    @Resource
    private TeamMemberMapper teamMemberMapper;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private TaskAssignmentLogMapper taskAssignmentLogMapper;

    @Resource
    private PermissionService permissionService;

    @Resource
    private TeamMembershipTerminationPolicy terminationPolicy;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private WeeklyReviewMapper weeklyReviewMapper;

    @Resource
    private KnowledgeIndexEventPublisher knowledgeIndexEventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamMembershipTerminationVO leaveTeam(Long teamId) {
        Long actorUserId = requireLoginUserId();
        validateTeamId(teamId);

        // 非锁定预检查只负责快速拒绝；锁内策略才是本事务的权威二次判断。
        permissionService.requireTeamLeave(actorUserId, teamId);

        List<TeamMember> lockedMembers = teamMemberMapper
                .selectActiveMembersForUpdate(teamId, List.of(actorUserId));
        TeamMember targetMember = terminationPolicy.requireLeaveAllowed(
                actorUserId, teamId, lockedMembers);
        return terminateLocked(
                actorUserId,
                targetMember,
                TaskAssignmentActionEnum.MEMBER_LEFT
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamMembershipTerminationVO removeMember(
            TeamMemberRemoveRequest request
    ) {
        Long actorUserId = requireLoginUserId();
        validateRemoveRequest(request);

        Long teamId = request.getTeamId();
        Long targetUserId = request.getTargetUserId();
        if (actorUserId.equals(targetUserId)) {
            throw new PermissionDeniedException();
        }

        permissionService.requireTeamMemberRemove(
                actorUserId, teamId, targetUserId);

        List<TeamMember> lockedMembers = teamMemberMapper
                .selectActiveMembersForUpdate(
                        teamId,
                        List.of(actorUserId, targetUserId)
                );
        TeamMember targetMember = terminationPolicy.requireRemoveAllowed(
                actorUserId,
                teamId,
                targetUserId,
                lockedMembers
        );
        return terminateLocked(
                actorUserId,
                targetMember,
                TaskAssignmentActionEnum.MEMBER_REMOVED
        );
    }

    private TeamMembershipTerminationVO terminateLocked(
            Long actorUserId,
            TeamMember targetMember,
            TaskAssignmentActionEnum action
    ) {
        LocalDateTime operationTime = LocalDateTime.now();

        List<MembershipTaskCleanupRow> lockedTasks = taskMapper
                .selectIncompleteAssignedTeamTasksForUpdate(
                        targetMember.getTeamId(),
                        targetMember.getUserId()
                );
        List<Long> taskIds = validateLockedTasks(
                lockedTasks,
                targetMember.getUserId()
        );
        List<Long> affectedReviewIds = findAffectedReviewIds(targetMember);

        int updatedCount = 0;
        if (!taskIds.isEmpty()) {
            updatedCount = taskMapper.bulkUnassignIncompleteTeamTasks(
                    targetMember.getTeamId(),
                    targetMember.getUserId(),
                    taskIds,
                    actorUserId,
                    operationTime
            );
            if (updatedCount != taskIds.size()) {
                throw new BusinessException(
                        ErrorCode.OPERATION_ERROR,
                        "操作状态已变化，请刷新后重试"
                );
            }
        }

        if (updatedCount > 0) {
            List<TaskAssignmentLog> logs = buildTerminationLogs(
                    taskIds,
                    targetMember.getUserId(),
                    actorUserId,
                    action,
                    operationTime
            );
            int logCount = taskAssignmentLogMapper
                    .batchInsertMembershipTerminationLogs(logs);
            if (logCount != updatedCount) {
                throw new BusinessException(
                        ErrorCode.SYSTEM_ERROR,
                        "终止审计写入失败"
                );
            }
        }

        int memberRows = teamMemberMapper.deactivateMembershipCas(
                targetMember.getId(),
                targetMember.getTeamId(),
                targetMember.getUserId(),
                targetMember.getRole(),
                operationTime
        );
        if (memberRows != 1) {
            throw new BusinessException(
                    ErrorCode.OPERATION_ERROR,
                    "成员关系状态已变化，请刷新后重试"
            );
        }

        if (knowledgeIndexEventPublisher != null) {
            knowledgeIndexEventPublisher.publishAll(KnowledgeSourceTypeEnum.TASK,
                    taskIds, KnowledgeEventTypeEnum.ACCESS_CHANGED);
            knowledgeIndexEventPublisher.publishAll(KnowledgeSourceTypeEnum.WEEKLY_REVIEW,
                    affectedReviewIds, KnowledgeEventTypeEnum.ACCESS_CHANGED);
        }

        TeamMembershipTerminationVO result =
                new TeamMembershipTerminationVO();
        result.setTeamId(targetMember.getTeamId());
        result.setMemberUserId(targetMember.getUserId());
        result.setAction(action.getValue());
        result.setUnassignedTaskCount(updatedCount);
        result.setTerminatedAt(operationTime);
        return result;
    }

    private List<Long> findAffectedReviewIds(TeamMember targetMember) {
        if (projectMapper == null || weeklyReviewMapper == null) {
            return List.of();
        }
        List<Long> projectIds = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                        .eq(Project::getTeamId, targetMember.getTeamId())
                        .eq(Project::getIsDelete, 0)
                        .isNull(Project::getDeletedAt))
                .stream().map(Project::getId).toList();
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return weeklyReviewMapper.selectList(new LambdaQueryWrapper<WeeklyReview>()
                        .eq(WeeklyReview::getUserId, targetMember.getUserId())
                        .in(WeeklyReview::getFocusProjectId, projectIds))
                .stream().map(WeeklyReview::getId).toList();
    }

    private List<Long> validateLockedTasks(
            List<MembershipTaskCleanupRow> lockedTasks,
            Long targetUserId
    ) {
        if (lockedTasks == null) {
            throw new BusinessException(
                    ErrorCode.SYSTEM_ERROR,
                    "任务清理查询失败"
            );
        }
        if (lockedTasks.isEmpty()) {
            return List.of();
        }

        List<Long> taskIds = new ArrayList<>(lockedTasks.size());
        Set<Long> uniqueIds = new HashSet<>();
        long previousId = Long.MIN_VALUE;
        for (MembershipTaskCleanupRow row : lockedTasks) {
            if (row == null
                    || row.getTaskId() == null
                    || row.getTaskId() <= 0
                    || !uniqueIds.add(row.getTaskId())
                    || row.getTaskId() <= previousId
                    || !java.util.Objects.equals(
                    targetUserId, row.getAssigneeUserId())) {
                throw new BusinessException(
                        ErrorCode.SYSTEM_ERROR,
                        "任务清理数据不合法"
                );
            }
            previousId = row.getTaskId();
            taskIds.add(row.getTaskId());
        }
        return taskIds;
    }

    private List<TaskAssignmentLog> buildTerminationLogs(
            List<Long> taskIds,
            Long memberUserId,
            Long actorUserId,
            TaskAssignmentActionEnum action,
            LocalDateTime operationTime
    ) {
        List<TaskAssignmentLog> logs = new ArrayList<>(taskIds.size());
        for (Long taskId : taskIds) {
            TaskAssignmentLog log = new TaskAssignmentLog();
            log.setId(IdWorker.getId());
            log.setTaskId(taskId);
            log.setFromAssigneeUserId(memberUserId);
            log.setToAssigneeUserId(null);
            log.setAssignedByUserId(actorUserId);
            log.setAction(action.getValue());
            log.setReason(null);
            log.setCreateTime(operationTime);
            logs.add(log);
        }
        return logs;
    }

    private void validateRemoveRequest(TeamMemberRemoveRequest request) {
        if (request == null
                || request.getTeamId() == null
                || request.getTeamId() <= 0
                || request.getTargetUserId() == null
                || request.getTargetUserId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
    }

    private void validateTeamId(Long teamId) {
        if (teamId == null || teamId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "teamId 不合法");
        }
    }

    private Long requireLoginUserId() {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return userId;
    }
}
