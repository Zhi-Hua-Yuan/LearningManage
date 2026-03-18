package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.utils.UserHolder;
import com.spt.learningmanage.mapper.ProjectMapper;
import com.spt.learningmanage.mapper.TaskMapper;
import com.spt.learningmanage.model.dto.task.TaskCreateRequest;
import com.spt.learningmanage.model.dto.task.TaskQueryRequest;
import com.spt.learningmanage.model.dto.task.TaskUpdateRequest;
import com.spt.learningmanage.model.entity.Project;
import com.spt.learningmanage.model.entity.Task;
import com.spt.learningmanage.model.vo.TaskVo;
import com.spt.learningmanage.service.TaskService;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class TaskServiceImpl implements TaskService {

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private ProjectMapper projectMapper;

    /**
     * 创建任务，返回任务ID。
     * 校验 projectId 是否存在（由于 Project 实体暂无 userId 字段，仅校验存在性）。
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
        if (request.getProjectId() != null) {
            Project project = projectMapper.selectById(request.getProjectId());
            if (project == null || project.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目不存在");
            }
        }
        validateTitle(request.getTitle());
        validatePriority(request.getPriority());

        Task task = new Task();
        task.setTitle(request.getTitle().trim());
        task.setDescription(request.getDescription());
        task.setProjectId(request.getProjectId());
        task.setUserId(userId);
        task.setStatus(0); // 默认未完成
        task.setPriority(request.getPriority());
        task.setDueDate(request.getDueDate());
        task.setIsDelete(0);

        int rows = taskMapper.insert(task);
        if (rows != 1 || task.getId() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建任务失败");
        }
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
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Task::getId, request.getId()).eq(Task::getUserId, userId);
        Task existing = taskMapper.selectOne(queryWrapper);
        if (existing == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务不存在");
        }

        String newTitle = request.getTitle() != null ? request.getTitle().trim() : existing.getTitle();
        validateTitle(newTitle);
        String newDescription = request.getDescription() != null ? request.getDescription() : existing.getDescription();
        Integer newStatus = request.getStatus() != null ? request.getStatus() : existing.getStatus();
        validateStatus(newStatus);
        Integer newPriority = request.getPriority() != null ? request.getPriority() : existing.getPriority();
        validatePriority(newPriority);
        LocalDate newDueDate = request.getDueDate() != null ? request.getDueDate() : existing.getDueDate();

        Task update = new Task();
        update.setId(request.getId());
        update.setTitle(newTitle);
        update.setDescription(newDescription);
        update.setStatus(newStatus);
        update.setPriority(newPriority);
        update.setDueDate(newDueDate);
        update.setUserId(userId);

        // 处理 completedAt
        if (!Objects.equals(existing.getStatus(), newStatus)) {
            if (newStatus == 1 && existing.getStatus() != 1) { // 从未完成到完成
                update.setCompletedAt(LocalDateTime.now());
            } else if (newStatus == 0 && existing.getStatus() == 1) { // 从完成到未完成
                update.setCompletedAt(null);
            }
        }

        int rows = taskMapper.updateById(update);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新任务失败");
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
        if (status != 0 && status != 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务状态不合法");
        }
    }

    /**
     * 校验任务优先级（假设 1-低, 2-中, 3-高）。
     */
    private void validatePriority(Integer priority) {
        if (priority != null && (priority < 1 || priority > 3)) {
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
