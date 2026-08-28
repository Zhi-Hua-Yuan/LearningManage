package com.spt.learningmanage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.dto.task.TaskBatchRenameRequest;
import com.spt.learningmanage.model.dto.task.TaskBatchRollbackRequest;
import com.spt.learningmanage.model.dto.task.TaskAssignRequest;
import com.spt.learningmanage.model.dto.task.TaskCreateRequest;
import com.spt.learningmanage.model.dto.task.TaskQueryRequest;
import com.spt.learningmanage.model.dto.task.TaskStatusChangeRequest;
import com.spt.learningmanage.model.dto.task.TaskUpdateRequest;
import com.spt.learningmanage.model.vo.task.TaskBatchRenameVO;
import com.spt.learningmanage.model.vo.task.TaskBatchRollbackVO;
import com.spt.learningmanage.model.vo.task.TaskAssignVO;
import com.spt.learningmanage.model.vo.task.TaskStatusChangeVO;
import com.spt.learningmanage.model.vo.task.TaskVo;
import com.spt.learningmanage.service.TaskService;
import com.spt.learningmanage.service.TaskAssignmentService;
import jakarta.annotation.Resource;
import org.springframework.util.StringUtils;
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

    @Resource
    private TaskAssignmentService taskAssignmentService;

    @PostMapping("/add")
    public BaseResponse<Long> addTask(@RequestBody TaskCreateRequest taskCreateRequest) {
        return ResultUtils.success(taskService.create(taskCreateRequest));
    }

    @PostMapping("/assign")
    public BaseResponse<TaskAssignVO> assignTask(@RequestBody TaskAssignRequest request) {
        return ResultUtils.success(taskAssignmentService.assign(request));
    }

    @GetMapping("/get/{id}")
    public BaseResponse<TaskVo> getTaskById(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        return ResultUtils.success(taskService.getById(id));
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
        return ResultUtils.success(taskService.list(queryRequest));
    }

    @PostMapping("/update")
    public BaseResponse<Boolean> updateTask(@RequestBody TaskUpdateRequest taskUpdateRequest) {
        if (taskUpdateRequest == null || taskUpdateRequest.getId() == null || taskUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        if (taskUpdateRequest.getStatus() != null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "状态变更请使用 /task/status/change 接口");
        }
        taskService.update(taskUpdateRequest);
        return ResultUtils.success(true);
    }

    @PostMapping("/status/change")
    public BaseResponse<TaskStatusChangeVO> changeStatus(@RequestBody TaskStatusChangeRequest request) {
        if (request == null || request.getTaskId() == null || request.getTaskId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        if (request.getTargetStatus() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "targetStatus 不能为空");
        }
        if (!StringUtils.hasText(request.getClientRequestId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "clientRequestId 不能为空");
        }
        return ResultUtils.success(taskService.changeStatus(request));
    }

    @PostMapping("/delete/{id}")
    public BaseResponse<Boolean> deleteTask(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        taskService.delete(id);
        return ResultUtils.success(true);
    }

    @PostMapping("/batch-rename")
    public BaseResponse<TaskBatchRenameVO> batchRename(@RequestBody TaskBatchRenameRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        return ResultUtils.success(taskService.batchRenameTitles(request));
    }

    @PostMapping("/batch-rename/rollback")
    public BaseResponse<TaskBatchRollbackVO> rollbackBatchRename(@RequestBody TaskBatchRollbackRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        return ResultUtils.success(taskService.rollbackBatchRename(request));
    }
}
