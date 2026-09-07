package com.spt.learningmanage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.model.dto.ops.CleanupRunCreateRequest;
import com.spt.learningmanage.model.vo.ops.*;
import com.spt.learningmanage.service.AiOpsQueryService;
import com.spt.learningmanage.service.CleanupRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "AI production operations")
@RestController
@RequestMapping("/admin/ai/ops")
public class AiOpsController {
    private final AiOpsQueryService ops;
    private final CleanupRunService cleanup;

    public AiOpsController(AiOpsQueryService ops, CleanupRunService cleanup) {
        this.ops = ops;
        this.cleanup = cleanup;
    }

    @GetMapping("/overview")
    public BaseResponse<AiOpsOverviewVO> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResultUtils.success(ops.overview(from, to));
    }

    @GetMapping("/rag")
    public BaseResponse<AiOpsSummaryVO> rag(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResultUtils.success(ops.rag(from, to));
    }

    @GetMapping("/agent")
    public BaseResponse<AiOpsSummaryVO> agent(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResultUtils.success(ops.agent(from, to));
    }

    @GetMapping("/failures")
    public BaseResponse<Page<OpsFailureVO>> failures(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ResultUtils.success(ops.failures(from, to, current, size));
    }

    @GetMapping("/dependencies")
    public BaseResponse<Map<String, DependencyStatusVO>> dependencies() {
        return ResultUtils.success(ops.dependencies());
    }

    @GetMapping("/cleanup-runs")
    public BaseResponse<Page<CleanupRunVO>> cleanupRuns(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ResultUtils.success(cleanup.list(current, size));
    }

    @GetMapping("/cleanup-runs/{runId}")
    public BaseResponse<CleanupRunVO> cleanupRun(@PathVariable String runId) {
        return ResultUtils.success(cleanup.get(runId));
    }

    @PostMapping("/cleanup-runs")
    @Operation(summary = "Submit a dry-run or approved cleanup")
    public BaseResponse<CleanupRunVO> submitCleanup(@Valid @RequestBody CleanupRunCreateRequest request) {
        return ResultUtils.success(cleanup.submit(request));
    }

    @PostMapping("/cleanup-runs/{runId}/cancel")
    public BaseResponse<CleanupCancelVO> cancelCleanup(@PathVariable String runId) {
        return ResultUtils.success(cleanup.cancel(runId));
    }
}
