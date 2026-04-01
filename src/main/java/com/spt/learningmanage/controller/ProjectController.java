package com.spt.learningmanage.controller;

import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.dto.project.ProjectCreateRequest;
import com.spt.learningmanage.model.dto.project.ProjectQueryRequest;
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

    // 创建项目，返回项目ID
    @PostMapping("/add")
    public BaseResponse<Long> addProject(@RequestBody ProjectCreateRequest projectCreateRequest) {
        return ResultUtils.ok(projectService.create(projectCreateRequest));
    }

    // 根据 id 获取项目详情（VO）
    @GetMapping("/get/{id}")
    public BaseResponse<ProjectVo> getProjectById(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目 ID 不合法");
        }
        return ResultUtils.ok(projectService.getById(id));
    }

    // 分页获取项目列表（VO）
    @GetMapping("/list")
    public BaseResponse<com.baomidou.mybatisplus.extension.plugins.pagination.Page<ProjectVo>> listProject(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Long pageSize,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "keyword", required = false) String keyword) {
        ProjectQueryRequest request = new ProjectQueryRequest();
        request.setPageNum(pageNum);
        request.setPageSize(pageSize);
        request.setStatus(status);
        request.setKeyword(keyword);
        return ResultUtils.ok(projectService.list(request));
    }

    // 更新项目
    @PostMapping("/update")
    public BaseResponse<Boolean> updateProject(@RequestBody ProjectUpdateRequest projectUpdateRequest) {
        if (projectUpdateRequest == null || projectUpdateRequest.getId() == null || projectUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目 ID 不合法");
        }
        projectService.update(projectUpdateRequest);
        return ResultUtils.ok(true);
    }

    // 归档项目
    @PostMapping("/archive")
    public BaseResponse<Boolean> archiveProject(@RequestBody List<Long> projectIds) {
        projectService.archive(projectIds);
        return ResultUtils.ok(true);
    }

    // 删除项目
    @PostMapping("/delete/{id}")
    public BaseResponse<Boolean> deleteProject(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目 ID 不合法");
        }
        projectService.delete(id);
        return ResultUtils.ok(true);
    }

    // 恢复项目
    @PostMapping("/recover/{id}")
    public BaseResponse<Boolean> recoverProject(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目 ID 不合法");
        }
        projectService.recover(id);
        return ResultUtils.ok(true);
    }
}
