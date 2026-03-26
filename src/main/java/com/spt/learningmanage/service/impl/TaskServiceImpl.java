package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.constant.TaskStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.utils.UserHolder;
import com.spt.learningmanage.mapper.MilestoneMapper;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.model.dto.task.TaskCreateRequest;
import com.spt.learningmanage.model.dto.task.TaskQueryRequest;
import com.spt.learningmanage.model.dto.task.TaskUpdateRequest;
import com.spt.learningmanage.model.entity.Milestone;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.vo.TaskVo;
import com.spt.learningmanage.service.TaskService;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class TaskServiceImpl implements TaskService {

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private MilestoneMapper milestoneMapper;

    /**
     * 创建任务，返回任务ID。
     * 强制校验项目归属当前用户。
     */
    @Override
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
        validateProjectOwnership(request.getProjectId(), userId);
        validateMilestoneOwnership(request.getProjectId(), request.getMilestoneId(), userId);
        validateTitle(request.getTitle());
        validatePriority(request.getPriority());

        Task task = new Task();
        task.setTitle(request.getTitle().trim());
        task.setDescription(request.getDescription());
        task.setProjectId(request.getProjectId());
        task.setMilestoneId(request.getMilestoneId());
        task.setUserId(userId);
        task.setStatus(0); // 默认未完成
        task.setPriority(request.getPriority());
        task.setDueDate(request.getDueDate());
        task.setIsDelete(0);

        int rows = taskMapper.insert(task);
        if (rows != 1 || task.getId() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建任务失败");
        }

        calculateAndUpdateProgress(task.getProjectId(), task.getMilestoneId());
        return task.getId();
    }

    /**
     * 根据ID查询任务详情，强制过滤 userId。
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
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Task::getId, id).eq(Task::getUserId, userId);
        Task task = taskMapper.selectOne(wrapper);
        if (task == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务不存在");
        }
        return toVo(task);
    }

    /**
     * 分页查询任务列表，强制过滤 userId。
     */
    @Override
    public Page<TaskVo> list(TaskQueryRequest request) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        TaskQueryRequest validRequest = request == null ? new TaskQueryRequest() : request;
        long pageNum = safePageNum(validRequest.getPageNum());
        long pageSize = safePageSize(validRequest.getPageSize());

        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Task::getUserId, userId);
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
        voPage.setRecords(resultPage.getRecords().stream().map(this::toVo).toList());
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

        // 1. 查询任务
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Task::getId, request.getId()).eq(Task::getUserId, userId);
        Task existing = taskMapper.selectOne(queryWrapper);
        if (existing == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务不存在");
        }

        // 2. 提取并校验新值
        String newTitle = request.getTitle() != null ? request.getTitle().trim() : existing.getTitle();
        validateTitle(newTitle);

        String newDescription = request.getDescription() != null ? request.getDescription() : existing.getDescription();

        Integer newStatus = request.getStatus() != null ? request.getStatus() : existing.getStatus();
        validateStatus(newStatus); // ⚠️ 内部建议改用 TaskStatusEnum.fromValue(value) 校验

        Integer newPriority = request.getPriority() != null ? request.getPriority() : existing.getPriority();
        validatePriority(newPriority);

        LocalDate newDueDate = request.getDueDate();

        Long milestoneId = request.getMilestoneId();

        // 3. 使用 UpdateWrapper 构造更新
        LambdaUpdateWrapper<Task> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Task::getId, request.getId())
                .eq(Task::getUserId, userId)
                .set(Task::getTitle, newTitle)
                .set(Task::getDescription, newDescription)
                .set(Task::getStatus, newStatus)
                .set(Task::getPriority, newPriority)
                .set(Task::getDueDate, newDueDate)
                .set(Task::getMilestoneId, milestoneId);

        // 4. 处理 completedAt (使用枚举值对比)
        int doneValue = TaskStatusEnum.DONE.getValue();

        if (!Objects.equals(existing.getStatus(), newStatus)) {
            // 状态变化后：新状态为完成则记录时间，否则清空时间。
            if (Objects.equals(newStatus, doneValue)) {
                updateWrapper.set(Task::getCompletedAt, LocalDateTime.now());
            } else {
                updateWrapper.set(Task::getCompletedAt, null);
            }
        }

        // 5. 执行更新
        int rows = taskMapper.update(null, updateWrapper);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新任务失败");
        }

        if (!Objects.equals(existing.getStatus(), newStatus)) {
            calculateAndUpdateProgress(existing.getProjectId(), existing.getMilestoneId());
        }
    }

    /**
     * 删除任务，强制过滤 userId。
     */
    @Override
    public void delete(Long id) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务 ID 不能为空");
        }
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Task::getId, id).eq(Task::getUserId, userId);
        Task existing = taskMapper.selectOne(queryWrapper);
        if (existing == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务不存在");
        }

        int rows = taskMapper.delete(queryWrapper);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除任务失败");
        }

        calculateAndUpdateProgress(existing.getProjectId(), existing.getMilestoneId());
    }

    /**
     * 计算并更新项目/里程碑进度。
     */
    private void calculateAndUpdateProgress(Long projectId, Long milestoneId) {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        if (projectId != null) {
            BigDecimal projectProgress = calculateProgressByCondition(projectId, null, userId);
            UpdateWrapper<Project> projectUpdateWrapper = new UpdateWrapper<>();
            projectUpdateWrapper.eq("id", projectId)
                    .eq("user_id", userId)
                    .set("progress", projectProgress);
            projectMapper.update(null, projectUpdateWrapper);
        }

        if (milestoneId != null) {
            BigDecimal milestoneProgress = calculateProgressByCondition(projectId, milestoneId, userId);
            UpdateWrapper<Milestone> milestoneUpdateWrapper = new UpdateWrapper<>();
            milestoneUpdateWrapper.eq("id", milestoneId)
                    .eq("user_id", userId)
                    .set("progress", milestoneProgress);
            milestoneMapper.update(null, milestoneUpdateWrapper);
        }
    }

    private BigDecimal calculateProgressByCondition(Long projectId, Long milestoneId, Long userId) {
        QueryWrapper<Task> totalWrapper = new QueryWrapper<>();
        totalWrapper.eq("user_id", userId);
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
        doneWrapper.eq("user_id", userId)
                .eq("status", TaskStatusEnum.DONE.getValue());
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

    private void validateProjectOwnership(Long projectId, Long userId) {
        LambdaQueryWrapper<Project> projectWrapper = new LambdaQueryWrapper<>();
        projectWrapper.eq(Project::getId, projectId)
                .eq(Project::getUserId, userId)
                .isNull(Project::getDeletedAt);
        Project project = projectMapper.selectOne(projectWrapper);
        if (project == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
    }

    private void validateMilestoneOwnership(Long projectId, Long milestoneId, Long userId) {
        if (milestoneId == null) {
            return;
        }
        if (milestoneId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "里程碑 ID 不合法");
        }
        LambdaQueryWrapper<Milestone> milestoneWrapper = new LambdaQueryWrapper<>();
        milestoneWrapper.eq(Milestone::getId, milestoneId)
                .eq(Milestone::getProjectId, projectId)
                .eq(Milestone::getUserId, userId);
        Milestone milestone = milestoneMapper.selectOne(milestoneWrapper);
        if (milestone == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "里程碑不存在或不属于当前项目");
        }
    }

    /**
     * 将实体转换为VO。
     */
    private TaskVo toVo(Task task) {
        TaskVo vo = new TaskVo();
        BeanUtils.copyProperties(task, vo);
        return vo;
    }

    /**
     * 校验任务标题。
     */
    private void validateTitle(String title) {
        if (!StringUtils.hasText(title)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务标题不能为空");
        }
        if (title.length() > 100) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务标题长度不能超过100");
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
