package com.spt.learningmanage.controller;

import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.dto.milestone.MilestoneCreateRequest;
import com.spt.learningmanage.model.dto.milestone.MilestoneQueryRequest;
import com.spt.learningmanage.model.dto.milestone.MilestoneUpdateRequest;
import com.spt.learningmanage.model.vo.MilestoneVo;
import com.spt.learningmanage.service.MilestoneService;
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
@RequestMapping("/milestone")
public class MilestoneController {

    @Resource
    private MilestoneService milestoneService;

    @PostMapping("/add")
    public BaseResponse<Long> addMilestone(@RequestBody MilestoneCreateRequest request) {
        return ResultUtils.ok(milestoneService.create(request));
    }

    @GetMapping("/list")
    public BaseResponse<List<MilestoneVo>> listMilestone(
            @RequestParam("projectId") Long projectId,
            @RequestParam(value = "keyword", required = false) String keyword) {
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目 ID 不合法");
        }
        MilestoneQueryRequest request = new MilestoneQueryRequest();
        request.setProjectId(projectId);
        request.setKeyword(keyword);
        return ResultUtils.ok(milestoneService.list(request));
    }

    @PostMapping("/update")
    public BaseResponse<Boolean> updateMilestone(@RequestBody MilestoneUpdateRequest request) {
        if (request == null || request.getId() == null || request.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "里程碑 ID 不合法");
        }
        milestoneService.update(request);
        return ResultUtils.ok(true);
    }

    @PostMapping("/delete/{id}")
    public BaseResponse<Boolean> deleteMilestone(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "里程碑 ID 不合法");
        }
        milestoneService.delete(id);
        return ResultUtils.ok(true);
    }
}

