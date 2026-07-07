package com.spt.learningmanage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.dto.ai.AiCallLogQueryRequest;
import com.spt.learningmanage.model.dto.ai.AiCallLogStatsRequest;
import com.spt.learningmanage.model.vo.ai.AiCallLogDetailVO;
import com.spt.learningmanage.model.vo.ai.AiCallLogStatsVO;
import com.spt.learningmanage.model.vo.ai.AiCallLogVO;
import com.spt.learningmanage.service.AiCallLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Tag(name = "AI 调用记录", description = "AI 调用记录查询")
@RestController
@RequestMapping("/ai/call-log")
public class AiCallLogController {

    @Resource
    private AiCallLogService aiCallLogService;

    @Operation(summary = "分页查询 AI 调用记录")
    @GetMapping("/list")
    public BaseResponse<Page<AiCallLogVO>> list(
            @RequestParam(value = "scene", required = false) String scene,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "modelName", required = false) String modelName,
            @RequestParam(value = "promptType", required = false) String promptType,
            @RequestParam(value = "startTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "size", defaultValue = "10") Long size) {
        AiCallLogQueryRequest request = new AiCallLogQueryRequest();
        request.setScene(scene);
        request.setStatus(status);
        request.setModelName(modelName);
        request.setPromptType(promptType);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setPageNum(current);
        request.setPageSize(size);
        return ResultUtils.success(aiCallLogService.list(request));
    }

    @Operation(summary = "获取 AI 调用记录详情")
    @GetMapping("/get/{id}")
    public BaseResponse<AiCallLogDetailVO> getDetail(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "调用记录 ID 不合法");
        }
        return ResultUtils.success(aiCallLogService.getDetail(id));
    }

    @Operation(summary = "查询 AI 调用记录统计")
    @GetMapping("/stats")
    public BaseResponse<AiCallLogStatsVO> stats(
            @RequestParam(value = "scene", required = false) String scene,
            @RequestParam(value = "startTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        AiCallLogStatsRequest request = new AiCallLogStatsRequest();
        request.setScene(scene);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        return ResultUtils.success(aiCallLogService.getStats(request));
    }
}
