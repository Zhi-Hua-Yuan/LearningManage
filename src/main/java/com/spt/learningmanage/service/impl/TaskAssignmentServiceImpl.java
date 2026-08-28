package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spt.learningmanage.constant.TaskAssignmentActionEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.TaskAssignmentLogMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.model.dto.task.TaskAssignRequest;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.TaskAssignmentLog;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.model.vo.task.TaskAssignVO;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.TaskAssigneePolicy;
import com.spt.learningmanage.service.TaskAssignmentService;
import com.spt.learningmanage.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class TaskAssignmentServiceImpl implements TaskAssignmentService {

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private TaskAssignmentLogMapper taskAssignmentLogMapper;

    @Resource
    private PermissionService permissionService;

    @Resource
    private TaskAssigneePolicy taskAssigneePolicy;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskAssignVO assign(TaskAssignRequest request) {
        Long actorUserId = UserHolder.get();
        if (actorUserId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        validateRequest(request);
        permissionService.requireTaskAssign(actorUserId, request.getTaskId());

        Task task = taskMapper.selectOne(new LambdaQueryWrapper<Task>()
                .eq(Task::getId, request.getTaskId())
                .eq(Task::getIsDelete, 0)
                .last("limit 1"));
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        }

        ProjectAccessScope scope = permissionService.requireProjectManage(actorUserId, task.getProjectId());
        Long currentAssigneeUserId = task.getAssigneeUserId();
        Long targetAssigneeUserId = request.getAssigneeUserId();
        if (!Objects.equals(currentAssigneeUserId, request.getExpectedAssigneeUserId())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务负责人已变化，请刷新后重试");
        }
        taskAssigneePolicy.validateAssignmentTarget(scope, targetAssigneeUserId);

        if (Objects.equals(currentAssigneeUserId, targetAssigneeUserId)) {
            return toVo(task, false, currentAssigneeUserId);
        }

        LocalDateTime assignedAt = LocalDateTime.now();
        int rows = taskMapper.compareAndSetAssignee(
                task.getId(), currentAssigneeUserId, targetAssigneeUserId, actorUserId, assignedAt);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务负责人已被其他请求更新，请刷新后重试");
        }

        TaskAssignmentActionEnum action = TaskAssignmentActionEnum.resolve(
                currentAssigneeUserId, targetAssigneeUserId);
        TaskAssignmentLog log = new TaskAssignmentLog();
        log.setTaskId(task.getId());
        log.setFromAssigneeUserId(currentAssigneeUserId);
        log.setToAssigneeUserId(targetAssigneeUserId);
        log.setAssignedByUserId(actorUserId);
        log.setAction(action.getValue());
        log.setReason(normalizeReason(request.getReason()));
        log.setCreateTime(assignedAt);
        if (taskAssignmentLogMapper.insert(log) != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "负责人变更日志写入失败");
        }

        return toVo(task, true, targetAssigneeUserId, actorUserId, assignedAt);
    }

    private void validateRequest(TaskAssignRequest request) {
        if (request == null || request.getTaskId() == null || request.getTaskId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "taskId 不合法");
        }
        if (!request.isExpectedAssigneeUserIdPresent()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "expectedAssigneeUserId 必须显式提供");
        }
        if (request.getAssigneeUserId() != null && request.getAssigneeUserId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "assigneeUserId 不合法");
        }
        normalizeReason(request.getReason());
    }

    private String normalizeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        String normalized = reason.trim();
        if (normalized.length() > 200) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "reason 长度不能超过200");
        }
        for (int i = 0; i < normalized.length(); i++) {
            if (Character.isISOControl(normalized.charAt(i))) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "reason 不能包含控制字符");
            }
        }
        return normalized;
    }

    private TaskAssignVO toVo(Task task, boolean changed, Long assigneeUserId) {
        return toVo(task, changed, assigneeUserId, task.getAssignedByUserId(), task.getAssignedAt());
    }

    private TaskAssignVO toVo(Task task, boolean changed, Long assigneeUserId,
                              Long assignedByUserId, LocalDateTime assignedAt) {
        TaskAssignVO vo = new TaskAssignVO();
        vo.setTaskId(task.getId());
        vo.setChanged(changed);
        vo.setPreviousAssigneeUserId(task.getAssigneeUserId());
        vo.setAssigneeUserId(assigneeUserId);
        vo.setAssignedByUserId(assignedByUserId);
        vo.setAssignedAt(assignedAt);
        return vo;
    }
}
