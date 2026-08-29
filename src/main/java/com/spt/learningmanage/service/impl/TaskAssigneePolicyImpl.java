package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.TaskAssigneeQueryMapper;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.service.TaskAssigneePolicy;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class TaskAssigneePolicyImpl implements TaskAssigneePolicy {

    @Resource
    private TaskAssigneeQueryMapper taskAssigneeQueryMapper;

    @Override
    public Long resolveInitialAssignee(ProjectAccessScope scope, Long requestedAssigneeUserId) {
        if (scope == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目权限范围不能为空");
        }
        if (scope.isPersonalProject()) {
            if (requestedAssigneeUserId == null
                    || Objects.equals(scope.projectOwnerUserId(), requestedAssigneeUserId)) {
                return scope.projectOwnerUserId();
            }
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "个人项目负责人必须为项目所有者");
        }
        if (requestedAssigneeUserId == null) {
            return null;
        }
        if (requestedAssigneeUserId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "负责人必须是团队有效成员");
        }
        requireLockedActiveTeamAssignee(scope.teamId(), requestedAssigneeUserId);
        return requestedAssigneeUserId;
    }

    @Override
    public void validateAssignmentTarget(ProjectAccessScope scope, Long targetAssigneeUserId) {
        if (scope == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目权限范围不能为空");
        }
        if (targetAssigneeUserId == null) {
            return;
        }
        if (targetAssigneeUserId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "负责人 ID 不合法");
        }
        if (scope.isPersonalProject()) {
            if (!Objects.equals(scope.projectOwnerUserId(), targetAssigneeUserId)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "个人项目负责人必须为项目所有者");
            }
            return;
        }
        requireLockedActiveTeamAssignee(scope.teamId(), targetAssigneeUserId);
    }

    @Override
    public void validateReopenAssignee(ProjectAccessScope scope, Long currentAssigneeUserId) {
        if (scope == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目权限范围不能为空");
        }
        if (currentAssigneeUserId == null) {
            return;
        }
        if (currentAssigneeUserId <= 0) {
            throw reopenAssigneeInvalid();
        }
        if (scope.isPersonalProject()) {
            if (!Objects.equals(scope.projectOwnerUserId(), currentAssigneeUserId)) {
                throw reopenAssigneeInvalid();
            }
            return;
        }
        if (!hasLockedActiveTeamAssignee(scope.teamId(), currentAssigneeUserId)) {
            throw reopenAssigneeInvalid();
        }
    }

    private void requireLockedActiveTeamAssignee(Long teamId, Long assigneeUserId) {
        if (!hasLockedActiveTeamAssignee(teamId, assigneeUserId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "负责人必须是团队有效成员");
        }
    }

    private boolean hasLockedActiveTeamAssignee(Long teamId, Long assigneeUserId) {
        Long lockedUserId = taskAssigneeQueryMapper.selectActiveTeamAssigneeForUpdate(teamId, assigneeUserId);
        return Objects.equals(assigneeUserId, lockedUserId);
    }

    private BusinessException reopenAssigneeInvalid() {
        return new BusinessException(
                ErrorCode.OPERATION_ERROR,
                "当前负责人已不具备任务受理资格，请先转派或取消分配后再重新打开"
        );
    }
}
