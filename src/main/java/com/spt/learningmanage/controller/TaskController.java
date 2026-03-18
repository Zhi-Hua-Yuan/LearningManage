package com.spt.learningmanage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.dto.task.TaskCreateRequest;
import com.spt.learningmanage.model.dto.task.TaskQueryRequest;
import com.spt.learningmanage.model.dto.task.TaskUpdateRequest;
import com.spt.learningmanage.model.vo.task.TaskVo;
import com.spt.learningmanage.service.TaskService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/task")
public class TaskController {

    @Resource
    private TaskService taskService;

    // 创建任务，返回任务ID
    @PostMapping("/add")
    public BaseResponse<Long> addTask(@RequestBody TaskCreateRequest taskCreateRequest) {
        Long userId = 1L; // 默认用户ID
        return ResultUtils.ok(taskService.create(taskCreateRequest, userId));
    }

    // 根据 id 获取任务详情（VO）
    @GetMapping("/get/{id}")
    public BaseResponse<TaskVo> getTaskById(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "任务 ID 不合法");
        }
        Long userId = 1L; // 默认用户ID
        return ResultUtils.ok(taskService.getById(id, userId));
    }

    // 分页获取任务列表（VO）
    @GetMapping("/list")
    public BaseResponse<Page<TaskVo>> listTask(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "isOverdue", required = false) Boolean isOverdue,
            @RequestParam(value = "current", defaultValue = "1") int current,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        TaskQueryRequest queryRequest = new TaskQueryRequest();
        queryRequest.setProjectId(projectId);
        queryRequest.setStatus(status);
        queryRequest.setIsOverdue(isOverdue);
        queryRequest.setPageNum((long) current);
        queryRequest.setPageSize((long) size);
        Long userId = 1L; // 默认用户ID
        return ResultUtils.ok(taskService.list(queryRequest, userId));
    }

    // 更新任务
    @PostMapping("/update")
    public BaseResponse<Boolean> updateTask(@RequestBody TaskUpdateRequest taskUpdateRequest) {
        if (taskUpdateRequest == null || taskUpdateRequest.getId() == null || taskUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "任务 ID 不合法");
        }
        Long userId = 1L; // 默认用户ID
        taskService.update(taskUpdateRequest, userId);
        return ResultUtils.ok(true);
    }

    // 删除任务（加入回收站）
    @PostMapping("/delete/{id}")
    public BaseResponse<Boolean> deleteTask(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "任务 ID 不合法");
        }
        Long userId = 1L; // 默认用户ID
        taskService.delete(id, userId);
        return ResultUtils.ok(true);
    }

}
