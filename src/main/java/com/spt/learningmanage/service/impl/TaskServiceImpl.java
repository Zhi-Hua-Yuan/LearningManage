package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.constant.DeleteSourceConstant;
import com.spt.learningmanage.constant.TaskStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.exception.PermissionDeniedException;
import com.spt.learningmanage.utils.UserHolder;
import com.spt.learningmanage.mapper.MilestoneMapper;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.mapper.TaskStatusIdempotencyMapper;
import com.spt.learningmanage.mapper.TaskTitleRenameLogMapper;
import com.spt.learningmanage.model.dto.task.TaskBatchRenameRequest;
import com.spt.learningmanage.model.dto.task.TaskBatchRollbackRequest;
import com.spt.learningmanage.model.dto.task.TaskCreateRequest;
import com.spt.learningmanage.model.dto.task.TaskQueryRequest;
import com.spt.learningmanage.model.dto.task.TaskRenameItemDTO;
import com.spt.learningmanage.model.dto.task.TaskStatusChangeRequest;
import com.spt.learningmanage.model.dto.task.TaskUpdateRequest;
import com.spt.learningmanage.model.entity.Milestone;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.entity.TaskStatusIdempotency;
import com.spt.learningmanage.model.entity.TaskTitleRenameLog;
import com.spt.learningmanage.model.vo.task.TaskBatchRenameVO;
import com.spt.learningmanage.model.vo.task.TaskBatchRollbackVO;
import com.spt.learningmanage.model.vo.task.TaskStatusChangeVO;
import com.spt.learningmanage.model.vo.task.TaskVo;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.model.permission.TaskCapabilities;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.TaskAssigneePolicy;
import com.spt.learningmanage.service.TaskCreationService;
import com.spt.learningmanage.service.TaskService;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class TaskServiceImpl implements TaskService {

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private MilestoneMapper milestoneMapper;

    @Resource
    private TaskTitleRenameLogMapper taskTitleRenameLogMapper;

    @Resource
    private TaskStatusIdempotencyMapper taskStatusIdempotencyMapper;

    @Resource
    private PermissionService permissionService;

    @Resource
    private TaskCreationService taskCreationService;

    @Resource
    private TaskAssigneePolicy taskAssigneePolicy;

    /** 创建任务，返回任务 ID；项目范围由 PermissionService 判定。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TaskCreateRequest request) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        }
        if (request.getProjectId() == null || request.getProjectId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目 ID 不合法");
        }
        ProjectAccessScope projectScope = permissionService.requireProjectCreateTask(userId, request.getProjectId());
        validateMilestoneBelongsToProject(request.getProjectId(), request.getMilestoneId());
        validateTitle(request.getTitle());
        validateDescription(request.getDescription());
        validatePriority(request.getPriority());

        Task task = new Task();
        task.setTitle(request.getTitle().trim());
        task.setDescription(request.getDescription());
        task.setProjectId(request.getProjectId());
        task.setMilestoneId(request.getMilestoneId());
        task.setStatus(0); // 默认未完成
        task.setPriority(request.getPriority());
        task.setDueDate(request.getDueDate());
        task.setIsDelete(0);
        task.setDeleteSource(DeleteSourceConstant.NORMAL);
        task.setDeletedAt(null);

        Long taskId = taskCreationService.createTask(task, projectScope, request.getAssigneeUserId());

        calculateAndUpdateProgress(task.getProjectId(), task.getMilestoneId());
        return taskId;
    }

    /**
     * 根据ID查询任务详情，先按统一权限范围判定可见性。
     */
    @Override
    public TaskVo getById(Long id) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务 ID 不能为空");
        }
        Map<Long, TaskCapabilities> capabilities = permissionService.resolveTaskCapabilities(userId, List.of(id));
        if (!capabilities.containsKey(id)) {
            throw new PermissionDeniedException();
        }
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Task::getId, id).eq(Task::getIsDelete, 0);
        Task task = taskMapper.selectOne(wrapper);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        }
        return toVo(task, capabilities.get(id));
    }

    /**
     * 分页查询任务列表，默认按创建人保持旧客户端行为。
     */
    @Override
    public Page<TaskVo> list(TaskQueryRequest request) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        permissionService.requireActiveActor(userId);
        TaskQueryRequest validRequest = request == null ? new TaskQueryRequest() : request;
        long pageNum = safePageNum(validRequest.getPageNum());
        long pageSize = safePageSize(validRequest.getPageSize());

        var projectScope = validRequest.getProjectId() == null
                ? null
                : permissionService.requireProjectView(userId, validRequest.getProjectId());

        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        if (projectScope == null || projectScope.isPersonalProject()) {
            wrapper.eq(Task::getCreatedByUserId, userId);
        }
        wrapper.eq(Task::getIsDelete, 0);
        if (validRequest.getStatus() != null) {
            wrapper.eq(Task::getStatus, validRequest.getStatus());
        }
        if (StringUtils.hasText(validRequest.getTitle())) {
            wrapper.like(Task::getTitle, validRequest.getTitle());
        }
        if (validRequest.getProjectId() != null) {
            wrapper.eq(Task::getProjectId, validRequest.getProjectId());
        }
        wrapper.orderByDesc(Task::getCreateTime);

        Page<Task> page = new Page<>(pageNum, pageSize);
        Page<Task> resultPage = taskMapper.selectPage(page, wrapper);
        Page<TaskVo> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        List<Long> taskIds = resultPage.getRecords().stream().map(Task::getId).toList();
        Map<Long, TaskCapabilities> capabilities = taskIds.isEmpty()
                ? Map.of()
                : permissionService.resolveTaskCapabilities(userId, taskIds);
        voPage.setRecords(resultPage.getRecords().stream()
                .filter(task -> capabilities.containsKey(task.getId()))
                .map(task -> toVo(task, capabilities.get(task.getId())))
                .toList());
        return voPage;
    }

    /**
     * 更新任务信息，强制过滤 userId。
     * 处理状态变化：从未完成到已完成设置 completedAt，从已完成到未完成清空 completedAt。
     */
    @Override
    public void update(TaskUpdateRequest request) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (request == null || request.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务 ID 不能为空");
        }

        // 1. 查询任务；授权由 PermissionService 根据当前数据库事实判定。
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Task::getId, request.getId()).eq(Task::getIsDelete, 0);
        Task existing = taskMapper.selectOne(queryWrapper);
        if (existing == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务不存在");
        }

        // 2. 提取并校验新值
        String newTitle = request.getTitle() != null ? request.getTitle().trim() : existing.getTitle();
        validateTitle(newTitle);

        String newDescription = request.getDescription() != null ? request.getDescription() : existing.getDescription();
        validateDescription(newDescription);
        Integer newStatus = existing.getStatus();

        if (request.getStatus() != null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "状态变更请使用 /task/status/change 接口");
        }
        validateStatus(newStatus); // ⚠️ 内部建议改用 TaskStatusEnum.fromValue(value) 校验

        Integer newPriority = request.getPriority() != null ? request.getPriority() : existing.getPriority();
        validatePriority(newPriority);

        LocalDate newDueDate = request.getDueDate() != null ? request.getDueDate() : existing.getDueDate();

        Long milestoneId = request.getMilestoneId() != null ? request.getMilestoneId() : existing.getMilestoneId();
        if (!Objects.equals(existing.getMilestoneId(), milestoneId)) {
            validateMilestoneBelongsToProject(existing.getProjectId(), milestoneId);
        }

        boolean contentChanged = !Objects.equals(existing.getTitle(), newTitle)
                || !Objects.equals(existing.getDescription(), newDescription)
                || !Objects.equals(existing.getDueDate(), newDueDate);
        boolean reorganizeChanged = !Objects.equals(existing.getPriority(), newPriority)
                || !Objects.equals(existing.getMilestoneId(), milestoneId);
        if (contentChanged) {
            permissionService.requireTaskEditContent(userId, request.getId());
        }
        if (reorganizeChanged) {
            permissionService.requireTaskReorganize(userId, request.getId());
        }
        if (!contentChanged && !reorganizeChanged) {
            permissionService.requireTaskView(userId, request.getId());
        }

        // 3. 使用 UpdateWrapper 构造更新
        LambdaUpdateWrapper<Task> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Task::getId, request.getId())
                .eq(Task::getIsDelete, 0)
                .set(Task::getTitle, newTitle)
                .set(Task::getDescription, newDescription)
                .set(Task::getPriority, newPriority)
                .set(Task::getDueDate, newDueDate)
                .set(Task::getMilestoneId, milestoneId);

        // 4. 处理 completedAt（0为未完成，1/2/3均视为完成）

        // 5. 执行更新
        int rows = taskMapper.update(null, updateWrapper);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新任务失败");
        }

    }

    /**
     * 删除任务，强制过滤 userId。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskStatusChangeVO changeStatus(TaskStatusChangeRequest request) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        validateChangeStatusRequest(request);
        permissionService.requireTaskChangeStatus(userId, request.getTaskId());

        String clientRequestId = request.getClientRequestId().trim();
        TaskStatusIdempotency idem = taskStatusIdempotencyMapper.selectOne(new LambdaQueryWrapper<TaskStatusIdempotency>()
                .eq(TaskStatusIdempotency::getUserId, userId)
                .eq(TaskStatusIdempotency::getTaskId, request.getTaskId())
                .eq(TaskStatusIdempotency::getClientRequestId, clientRequestId)
                .last("limit 1"));
        if (idem != null) {
            return toStatusChangeVO(idem, true);
        }

        Task task = taskMapper.selectOne(new LambdaQueryWrapper<Task>()
                .eq(Task::getId, request.getTaskId())
                .eq(Task::getIsDelete, 0)
                .last("limit 1"));
        if (task == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务不存在或无权限");
        }

        Integer oldStatus = task.getStatus();
        Integer targetStatus = request.getTargetStatus();
        validateStatus(targetStatus);
        validateStatusTransition(oldStatus, targetStatus);
        if (request.getExpectedStatus() != null && !Objects.equals(request.getExpectedStatus(), oldStatus)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务状态已变化，请刷新后重试");
        }

        boolean changed = false;
        Integer finalStatus = oldStatus;
        LocalDateTime finalCompletedAt = task.getCompletedAt();
        if (!Objects.equals(oldStatus, targetStatus)) {
            LocalDateTime newCompletedAt = resolveCompletedAt(oldStatus, targetStatus, task.getCompletedAt());
            boolean reopenTransition = TaskStatusEnum.isCompleted(oldStatus)
                    && Objects.equals(targetStatus, TaskStatusEnum.TODO.getValue());
            int rows;
            if (reopenTransition) {
                ProjectAccessScope projectScope = permissionService.requireProjectView(
                        userId,
                        task.getProjectId()
                );
                taskAssigneePolicy.validateReopenAssignee(
                        projectScope,
                        task.getAssigneeUserId()
                );
                rows = taskMapper.compareAndSetStatusForReopen(
                        request.getTaskId(),
                        oldStatus,
                        task.getAssigneeUserId(),
                        targetStatus,
                        newCompletedAt
                );
                if (rows == 0) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,
                            "任务状态或负责人已被其他请求更新，请刷新后重试");
                }
            } else {
                rows = taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                        .eq(Task::getId, request.getTaskId())
                        .eq(Task::getIsDelete, 0)
                        .eq(Task::getStatus, oldStatus)
                        .set(Task::getStatus, targetStatus)
                        .set(Task::getCompletedAt, newCompletedAt));
            }
            if (rows == 1) {
                changed = true;
                finalStatus = targetStatus;
                finalCompletedAt = newCompletedAt;
                calculateAndUpdateProgress(task.getProjectId(), task.getMilestoneId());
            } else {
                Task latest = taskMapper.selectOne(new LambdaQueryWrapper<Task>()
                        .eq(Task::getId, request.getTaskId())
                        .eq(Task::getIsDelete, 0)
                        .last("limit 1"));
                if (latest == null) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务不存在或无权限");
                }
                finalStatus = latest.getStatus();
                finalCompletedAt = latest.getCompletedAt();
                if (!Objects.equals(finalStatus, targetStatus)) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务状态已被其他请求更新，请刷新后重试");
                }
            }
        }

        TaskStatusIdempotency toSave = new TaskStatusIdempotency();
        toSave.setUserId(userId);
        toSave.setTaskId(request.getTaskId());
        toSave.setClientRequestId(clientRequestId);
        toSave.setTargetStatus(targetStatus);
        toSave.setChanged(changed ? 1 : 0);
        toSave.setFinalStatus(finalStatus);
        toSave.setCompletedAt(finalCompletedAt);
        try {
            taskStatusIdempotencyMapper.insert(toSave);
            return toStatusChangeVO(toSave, false);
        } catch (DuplicateKeyException e) {
            TaskStatusIdempotency duplicate = taskStatusIdempotencyMapper.selectOne(new LambdaQueryWrapper<TaskStatusIdempotency>()
                    .eq(TaskStatusIdempotency::getUserId, userId)
                    .eq(TaskStatusIdempotency::getTaskId, request.getTaskId())
                    .eq(TaskStatusIdempotency::getClientRequestId, clientRequestId)
                    .last("limit 1"));
            if (duplicate == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "幂等请求处理失败，请重试");
            }
            return toStatusChangeVO(duplicate, true);
        }
    }

    @Override
    public void delete(Long id) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务 ID 不能为空");
        }
        permissionService.requireTaskDelete(userId, id);
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Task::getId, id).eq(Task::getIsDelete, 0);
        Task existing = taskMapper.selectOne(queryWrapper);
        if (existing == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务不存在");
        }

        LambdaUpdateWrapper<Task> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Task::getId, id)
                .eq(Task::getIsDelete, 0)
                .set(Task::getIsDelete, 1)
                .set(Task::getDeleteSource, DeleteSourceConstant.MANUAL)
                .set(Task::getDeletedAt, LocalDateTime.now());

        int rows = taskMapper.update(null, updateWrapper);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除任务失败");
        }

        calculateAndUpdateProgress(existing.getProjectId(), existing.getMilestoneId());
    }

    /**
     * 计算并更新项目/里程碑进度。
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskBatchRenameVO batchRenameTitles(TaskBatchRenameRequest request) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (request == null || !StringUtils.hasText(request.getOperationId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "operationId 不能为空");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "items 不能为空");
        }

        String operationId = request.getOperationId().trim();
        List<TaskTitleRenameLog> logs = taskTitleRenameLogMapper.selectList(new LambdaQueryWrapper<TaskTitleRenameLog>()
                .eq(TaskTitleRenameLog::getOperationId, operationId)
                .eq(TaskTitleRenameLog::getUserId, userId)
                .eq(TaskTitleRenameLog::getIsRollback, 0));
        if (logs.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "operationId 不存在或无权限");
        }

        Map<Long, TaskTitleRenameLog> logMap = new HashMap<>();
        for (TaskTitleRenameLog log : logs) {
            if (log != null && log.getTaskId() != null) {
                logMap.put(log.getTaskId(), log);
            }
        }

        int successCount = 0;
        int skipCount = 0;
        List<Long> updatedTaskIds = new ArrayList<>();
        for (TaskRenameItemDTO item : request.getItems()) {
            if (item == null || item.getTaskId() == null || item.getTaskId() <= 0) {
                skipCount++;
                continue;
            }

            TaskTitleRenameLog log = logMap.get(item.getTaskId());
            if (log == null || Objects.equals(log.getIsRollback(), 1)) {
                skipCount++;
                continue;
            }

            String oldTitle = item.getOldTitle() == null ? null : item.getOldTitle().trim();
            String newTitle = item.getNewTitle() == null ? null : item.getNewTitle().trim();
            if (!StringUtils.hasText(oldTitle) || !StringUtils.hasText(newTitle)) {
                skipCount++;
                continue;
            }
            validateTitle(newTitle);

            if (!oldTitle.equals(log.getOldTitle()) || !newTitle.equals(log.getNewTitle())) {
                skipCount++;
                continue;
            }

            Task currentTask = taskMapper.selectOne(new LambdaQueryWrapper<Task>()
                    .eq(Task::getId, item.getTaskId())
                    .eq(Task::getCreatedByUserId, userId)
                    .last("limit 1"));
            if (currentTask == null || !oldTitle.equals(currentTask.getTitle())) {
                skipCount++;
                continue;
            }

            int updateRows = taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                    .eq(Task::getId, item.getTaskId())
                    .eq(Task::getCreatedByUserId, userId)
                    .eq(Task::getTitle, oldTitle)
                    .set(Task::getTitle, newTitle));
            if (updateRows == 1) {
                taskTitleRenameLogMapper.update(null, new LambdaUpdateWrapper<TaskTitleRenameLog>()
                        .eq(TaskTitleRenameLog::getId, log.getId())
                        .set(TaskTitleRenameLog::getIsApplied, 1)
                        .set(TaskTitleRenameLog::getAppliedAt, LocalDateTime.now()));
                successCount++;
                updatedTaskIds.add(item.getTaskId());
            } else {
                skipCount++;
            }
        }

        TaskBatchRenameVO result = new TaskBatchRenameVO();
        result.setOperationId(operationId);
        result.setSuccessCount(successCount);
        result.setSkipCount(skipCount);
        result.setUpdatedTaskIds(updatedTaskIds);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskBatchRollbackVO rollbackBatchRename(TaskBatchRollbackRequest request) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (request == null || !StringUtils.hasText(request.getOperationId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "operationId 不能为空");
        }

        String operationId = request.getOperationId().trim();
        List<TaskTitleRenameLog> logs = taskTitleRenameLogMapper.selectList(new LambdaQueryWrapper<TaskTitleRenameLog>()
                .eq(TaskTitleRenameLog::getOperationId, operationId)
                .eq(TaskTitleRenameLog::getUserId, userId)
                .eq(TaskTitleRenameLog::getIsApplied, 1)
                .eq(TaskTitleRenameLog::getIsRollback, 0));
        if (logs.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该批次没有可回滚的改名记录");
        }

        int rollbackCount = 0;
        LocalDateTime now = LocalDateTime.now();
        for (TaskTitleRenameLog log : logs) {
            if (log == null || log.getTaskId() == null) {
                continue;
            }
            int updateRows = taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                    .eq(Task::getId, log.getTaskId())
                    .eq(Task::getCreatedByUserId, userId)
                    .eq(Task::getTitle, log.getNewTitle())
                    .set(Task::getTitle, log.getOldTitle()));
            if (updateRows == 1) {
                taskTitleRenameLogMapper.update(null, new LambdaUpdateWrapper<TaskTitleRenameLog>()
                        .eq(TaskTitleRenameLog::getId, log.getId())
                        .set(TaskTitleRenameLog::getIsRollback, 1)
                        .set(TaskTitleRenameLog::getRollbackAt, now));
                rollbackCount++;
            }
        }

        TaskBatchRollbackVO result = new TaskBatchRollbackVO();
        result.setOperationId(operationId);
        result.setRollbackCount(rollbackCount);
        return result;
    }
    private void calculateAndUpdateProgress(Long projectId, Long milestoneId) {
        if (projectId != null) {
            BigDecimal projectProgress = calculateProgressByCondition(projectId, null);
            UpdateWrapper<Project> projectUpdateWrapper = new UpdateWrapper<>();
            projectUpdateWrapper.eq("id", projectId)
                    .set("progress", projectProgress);
            projectMapper.update(null, projectUpdateWrapper);
        }

        if (milestoneId != null) {
            BigDecimal milestoneProgress = calculateProgressByCondition(projectId, milestoneId);
            UpdateWrapper<Milestone> milestoneUpdateWrapper = new UpdateWrapper<>();
            milestoneUpdateWrapper.eq("id", milestoneId)
                    .set("progress", milestoneProgress);
            milestoneMapper.update(null, milestoneUpdateWrapper);
        }
    }

    private BigDecimal calculateProgressByCondition(Long projectId, Long milestoneId) {
        QueryWrapper<Task> totalWrapper = new QueryWrapper<>();
        totalWrapper.eq("is_delete", 0);
        if (projectId != null) {
            totalWrapper.eq("project_id", projectId);
        }
        if (milestoneId != null) {
            totalWrapper.eq("milestone_id", milestoneId);
        }
        Long total = taskMapper.selectCount(totalWrapper);
        if (total == null || total == 0) {
            return BigDecimal.ZERO;
        }

        QueryWrapper<Task> doneWrapper = new QueryWrapper<>();
        doneWrapper.eq("is_delete", 0)
                .in("status",
                        TaskStatusEnum.DONE_BASIC.getValue(),
                        TaskStatusEnum.DONE_STANDARD.getValue(),
                        TaskStatusEnum.DONE_EXCELLENT.getValue());
        if (projectId != null) {
            doneWrapper.eq("project_id", projectId);
        }
        if (milestoneId != null) {
            doneWrapper.eq("milestone_id", milestoneId);
        }
        Long done = taskMapper.selectCount(doneWrapper);
        long doneCount = done == null ? 0L : done;

        return BigDecimal.valueOf(doneCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private void validateMilestoneBelongsToProject(Long projectId, Long milestoneId) {
        if (milestoneId == null) {
            return;
        }
        if (milestoneId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "里程碑 ID 不合法");
        }
        LambdaQueryWrapper<Milestone> milestoneWrapper = new LambdaQueryWrapper<>();
        milestoneWrapper.eq(Milestone::getId, milestoneId)
                .eq(Milestone::getProjectId, projectId)
                .eq(Milestone::getIsDelete, 0);
        Milestone milestone = milestoneMapper.selectOne(milestoneWrapper);
        if (milestone == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "里程碑不存在或不属于当前项目");
        }
    }

    /**
     * 将实体转换为VO。
     */
    private TaskVo toVo(Task task, TaskCapabilities capabilities) {
        TaskVo vo = new TaskVo();
        BeanUtils.copyProperties(task, vo);
        vo.setCapabilities(capabilities);
        return vo;
    }

    /**
     * 校验任务标题。
     */
    private void validateTitle(String title) {
        if (!StringUtils.hasText(title)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务标题不能为空");
        }
        if (title.length() > 60) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务标题长度不能超过60");
        }
    }

    /**
     * 校验任务描述。
     */
    private void validateDescription(String description) {
        if (description != null && description.length() > 550) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务描述长度不能超过550");
        }
    }

    /**
     * 校验任务状态。
     */
    private void validateStatus(Integer status) {
        if (status == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "状态不能为空");
        }
        try {
            // 如果值非法，fromValue 会抛出 IllegalArgumentException
            TaskStatusEnum.fromValue(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务状态不合法");
        }
    }

    /**
     * 校验任务优先级（0-无, 1-低, 2-中, 3-高）。
     */
    private void validatePriority(Integer priority) {
        if (priority != null && (priority < 0 || priority > 3)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务优先级不合法");
        }
    }

    /**
     * 规范化页码。
     */
    private void validateChangeStatusRequest(TaskStatusChangeRequest request) {
        if (request == null || request.getTaskId() == null || request.getTaskId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "taskId 不合法");
        }
        if (!StringUtils.hasText(request.getClientRequestId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "clientRequestId 不能为空");
        }
        validateStatus(request.getTargetStatus());
        if (request.getExpectedStatus() != null) {
            validateStatus(request.getExpectedStatus());
        }
    }

    private void validateStatusTransition(Integer oldStatus, Integer newStatus) {
        if (Objects.equals(oldStatus, newStatus)) {
            return;
        }
        boolean oldCompleted = TaskStatusEnum.isCompleted(oldStatus);
        boolean newCompleted = TaskStatusEnum.isCompleted(newStatus);
        if (!oldCompleted && newCompleted) {
            return;
        }
        if (oldCompleted && !newCompleted && Objects.equals(newStatus, TaskStatusEnum.TODO.getValue())) {
            return;
        }
        if (oldCompleted && newCompleted) {
            return;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的任务状态流转");
    }

    private LocalDateTime resolveCompletedAt(Integer oldStatus, Integer newStatus, LocalDateTime oldCompletedAt) {
        boolean oldCompleted = TaskStatusEnum.isCompleted(oldStatus);
        boolean newCompleted = TaskStatusEnum.isCompleted(newStatus);
        if (!oldCompleted && newCompleted) {
            return oldCompletedAt != null ? oldCompletedAt : LocalDateTime.now();
        }
        if (oldCompleted && !newCompleted) {
            return null;
        }
        return oldCompletedAt;
    }

    private TaskStatusChangeVO toStatusChangeVO(TaskStatusIdempotency record, boolean replay) {
        TaskStatusChangeVO vo = new TaskStatusChangeVO();
        vo.setChanged(record.getChanged() != null && record.getChanged() == 1);
        vo.setFinalStatus(record.getFinalStatus());
        vo.setCompletedAt(record.getCompletedAt());
        vo.setIdempotentReplay(replay);
        return vo;
    }

    private long safePageNum(Long pageNum) {
        if (pageNum == null || pageNum < 1) {
            return 1L;
        }
        return pageNum;
    }

    /**
     * 规范化每页条数。
     */
    private long safePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10L;
        }
        return Math.min(pageSize, 100L);
    }
}
