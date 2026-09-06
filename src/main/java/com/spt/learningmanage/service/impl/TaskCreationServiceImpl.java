package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.constant.KnowledgeEventTypeEnum;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.constant.TaskAssignmentActionEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.TaskAssignmentLogMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.TaskAssignmentLog;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.service.TaskAssigneePolicy;
import com.spt.learningmanage.service.TaskCreationService;
import com.spt.learningmanage.service.KnowledgeIndexEventPublisher;
import com.spt.learningmanage.service.BusinessDataVersionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class TaskCreationServiceImpl implements TaskCreationService {

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private TaskAssignmentLogMapper taskAssignmentLogMapper;

    @Resource
    private TaskAssigneePolicy taskAssigneePolicy;

    @Resource
    private KnowledgeIndexEventPublisher knowledgeIndexEventPublisher;

    @Resource
    private BusinessDataVersionService businessDataVersionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(Task task, ProjectAccessScope scope, Long requestedAssigneeUserId) {
        if (task == null || scope == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务和项目权限范围不能为空");
        }
        Long assigneeUserId = taskAssigneePolicy.resolveInitialAssignee(scope, requestedAssigneeUserId);
        LocalDateTime now = LocalDateTime.now();
        task.setCreatedByUserId(scope.actorUserId());
        task.setAssigneeUserId(assigneeUserId);
        task.setAssignedByUserId(assigneeUserId == null ? null : scope.actorUserId());
        task.setAssignedAt(assigneeUserId == null ? null : now);

        int taskRows = taskMapper.insert(task);
        if (taskRows != 1 || task.getId() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建任务失败");
        }
        if (assigneeUserId != null) {
            TaskAssignmentLog log = new TaskAssignmentLog();
            log.setTaskId(task.getId());
            log.setFromAssigneeUserId(null);
            log.setToAssigneeUserId(assigneeUserId);
            log.setAssignedByUserId(scope.actorUserId());
            log.setAction(TaskAssignmentActionEnum.INITIAL_ASSIGN.getValue());
            log.setCreateTime(now);
            if (taskAssignmentLogMapper.insert(log) != 1) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "写入初始分配日志失败");
            }
        }
        if (knowledgeIndexEventPublisher != null) {
            knowledgeIndexEventPublisher.publish(
                    KnowledgeSourceTypeEnum.TASK, task.getId(), KnowledgeEventTypeEnum.SOURCE_CHANGED);
        }
        if (businessDataVersionService != null) {
            businessDataVersionService.incrementProjectAndOwningTeam(task.getProjectId());
        }
        return task.getId();
    }
}
