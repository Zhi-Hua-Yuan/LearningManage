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
        if (requestedAssigneeUserId <= 0
                || taskAssigneeQueryMapper.countActiveTeamAssignee(
                scope.teamId(), requestedAssigneeUserId) != 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "负责人必须是团队有效成员");
        }
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
        if (taskAssigneeQueryMapper.selectActiveTeamAssigneeForUpdate(
                scope.teamId(), targetAssigneeUserId) == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "负责人必须是团队有效成员");
        }
    }
}
