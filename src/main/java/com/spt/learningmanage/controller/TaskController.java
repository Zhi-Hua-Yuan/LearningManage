package com.spt.learningmanage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.dto.task.TaskBatchRenameRequest;
import com.spt.learningmanage.model.dto.task.TaskBatchRollbackRequest;
import com.spt.learningmanage.model.dto.task.TaskCreateRequest;
import com.spt.learningmanage.model.dto.task.TaskQueryRequest;
import com.spt.learningmanage.model.dto.task.TaskUpdateRequest;
import com.spt.learningmanage.model.vo.task.TaskBatchRenameVO;
import com.spt.learningmanage.model.vo.task.TaskBatchRollbackVO;
import com.spt.learningmanage.model.vo.task.TaskVo;
import com.spt.learningmanage.service.TaskService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/task")
public class TaskController {

    @Resource
    private TaskService taskService;

    @PostMapping("/add")
    public BaseResponse<Long> addTask(@RequestBody TaskCreateRequest taskCreateRequest) {
        return ResultUtils.ok(taskService.create(taskCreateRequest));
    }

    @GetMapping("/get/{id}")
    public BaseResponse<TaskVo> getTaskById(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务ID不合法");
        }
        return ResultUtils.ok(taskService.getById(id));
    }

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
        return ResultUtils.ok(taskService.list(queryRequest));
    }

    @PostMapping("/update")
    public BaseResponse<Boolean> updateTask(@RequestBody TaskUpdateRequest taskUpdateRequest) {
        if (taskUpdateRequest == null || taskUpdateRequest.getId() == null || taskUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务ID不合法");
        }
        taskService.update(taskUpdateRequest);
        return ResultUtils.ok(true);
    }

    @PostMapping("/delete/{id}")
    public BaseResponse<Boolean> deleteTask(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务ID不合法");
        }
        taskService.delete(id);
        return ResultUtils.ok(true);
    }

    @PostMapping("/batch-rename")
    public BaseResponse<TaskBatchRenameVO> batchRename(@RequestBody TaskBatchRenameRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求体不能为空");
        }
        return ResultUtils.ok(taskService.batchRenameTitles(request));
    }

    @PostMapping("/batch-rename/rollback")
    public BaseResponse<TaskBatchRollbackVO> rollbackBatchRename(@RequestBody TaskBatchRollbackRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求体不能为空");
        }
        return ResultUtils.ok(taskService.rollbackBatchRename(request));
    }
}