package com.spt.learningmanage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.model.dto.agent.AgentReportQueryRequest;
import com.spt.learningmanage.model.vo.agent.AnalysisReportVO;
import com.spt.learningmanage.service.AgentReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI Analysis Report", description = "用户确认后的 Agent 分析报告")
@RestController
@RequestMapping("/ai/report")
public class AnalysisReportController {
    private final AgentReportService reportService;

    public AnalysisReportController(AgentReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    @Operation(summary = "分页查询可访问的分析报告")
    public BaseResponse<Page<AnalysisReportVO>> list(@Valid @ModelAttribute AgentReportQueryRequest request) {
        return ResultUtils.success(reportService.list(request));
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "查询分析报告详情")
    public BaseResponse<AnalysisReportVO> get(@PathVariable String reportId) {
        return ResultUtils.success(reportService.get(reportId));
    }

    @PostMapping("/{reportId}/delete")
    @Operation(summary = "逻辑删除分析报告")
    public BaseResponse<Boolean> delete(@PathVariable String reportId) {
        return ResultUtils.success(reportService.delete(reportId));
    }
}

