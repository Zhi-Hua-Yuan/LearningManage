package com.spt.learningmanage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.dto.project.ProjectCreateRequest;
import com.spt.learningmanage.model.dto.project.ProjectQueryRequest;
import com.spt.learningmanage.model.dto.project.ProjectReorderRequest;
import com.spt.learningmanage.model.dto.project.ProjectUpdateRequest;
import com.spt.learningmanage.model.vo.project.ProjectVo;
import com.spt.learningmanage.service.ProjectService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/project")
public class ProjectController {

    @Resource
    private ProjectService projectService;

    @PostMapping("/add")
    public BaseResponse<Long> addProject(@RequestBody ProjectCreateRequest projectCreateRequest) {
        return ResultUtils.success(projectService.create(projectCreateRequest));
    }

    @GetMapping("/get/{id}")
    public BaseResponse<ProjectVo> getProjectById(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        return ResultUtils.success(projectService.getById(id));
    }

    @GetMapping("/list")
    public BaseResponse<Page<ProjectVo>> listProject(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "1000") Long pageSize,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "keyword", required = false) String keyword) {
        ProjectQueryRequest request = new ProjectQueryRequest();
        request.setPageNum(pageNum);
        request.setPageSize(pageSize);
        request.setStatus(status);
        request.setKeyword(keyword);
        return ResultUtils.success(projectService.list(request));
    }

    @PostMapping("/update")
    public BaseResponse<Boolean> updateProject(@RequestBody ProjectUpdateRequest projectUpdateRequest) {
        if (projectUpdateRequest == null || projectUpdateRequest.getId() == null || projectUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        projectService.update(projectUpdateRequest);
        return ResultUtils.success(true);
    }

    @PostMapping("/reorder")
    public BaseResponse<Boolean> reorderProject(@RequestBody List<ProjectReorderRequest> reorderRequests) {
        projectService.reorder(reorderRequests);
        return ResultUtils.success(true);
    }

    @PostMapping("/archive")
    public BaseResponse<Boolean> archiveProject(@RequestBody List<Long> projectIds) {
        projectService.archive(projectIds);
        return ResultUtils.success(true);
    }

    @PostMapping("/delete/{id}")
    public BaseResponse<Boolean> deleteProject(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        projectService.delete(id);
        return ResultUtils.success(true);
    }

    @PostMapping("/recover/{id}")
    public BaseResponse<Boolean> recoverProject(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不合法");
        }
        projectService.recover(id);
        return ResultUtils.success(true);
    }
}
