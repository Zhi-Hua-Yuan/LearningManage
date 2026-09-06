package com.spt.learningmanage.controller;

import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.model.dto.agent.AgentProjectRiskRequest;
import com.spt.learningmanage.model.dto.agent.AgentTeamWorkloadRequest;
import com.spt.learningmanage.model.dto.agent.AgentReportConfirmRequest;
import com.spt.learningmanage.model.vo.agent.AgentCancelVO;
import com.spt.learningmanage.model.vo.agent.AgentRunCreatedVO;
import com.spt.learningmanage.model.vo.agent.AgentRunVO;
import com.spt.learningmanage.service.AgentRunService;
import com.spt.learningmanage.service.AgentReportService;
import com.spt.learningmanage.model.vo.ai.AiDraftConfirmVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI Agent", description = "受控异步项目分析")
@RestController
@RequestMapping("/ai/agent")
public class AgentController {
    private final AgentRunService runService;
    private final AgentReportService reportService;

    public AgentController(AgentRunService runService, AgentReportService reportService) {
        this.runService = runService;
        this.reportService = reportService;
    }

    @PostMapping("/project-risk")
    @Operation(summary = "提交项目风险分析")
    public BaseResponse<AgentRunCreatedVO> projectRisk(@Valid @RequestBody AgentProjectRiskRequest request) {
        return ResultUtils.success(runService.submitProjectRisk(request));
    }

    @PostMapping("/team-workload")
    @Operation(summary = "提交团队负载分析")
    public BaseResponse<AgentRunCreatedVO> teamWorkload(@Valid @RequestBody AgentTeamWorkloadRequest request) {
        return ResultUtils.success(runService.submitTeamWorkload(request));
    }

    @GetMapping("/run/{runId}")
    @Operation(summary = "查询 Agent 运行状态")
    public BaseResponse<AgentRunVO> getRun(@PathVariable String runId) {
        return ResultUtils.success(runService.getRun(runId));
    }

    @PostMapping("/run/{runId}/cancel")
    @Operation(summary = "取消 Agent 运行")
    public BaseResponse<AgentCancelVO> cancel(@PathVariable String runId) {
        return ResultUtils.success(runService.cancel(runId));
    }

    @PostMapping("/report/confirm")
    @Operation(summary = "确认 Agent 分析草稿并保存报告")
    public BaseResponse<AiDraftConfirmVO> confirmReport(@Valid @RequestBody AgentReportConfirmRequest request) {
        return ResultUtils.success(reportService.confirm(request));
    }
}
