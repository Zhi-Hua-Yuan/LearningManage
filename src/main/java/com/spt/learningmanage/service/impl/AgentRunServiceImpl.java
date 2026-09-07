package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spt.learningmanage.config.AgentProperties;
import com.spt.learningmanage.constant.AgentOrchestrationModeEnum;
import com.spt.learningmanage.constant.AgentRunStatusEnum;
import com.spt.learningmanage.constant.AgentSceneEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiAgentRunMapper;
import com.spt.learningmanage.model.dto.agent.AgentProjectRiskRequest;
import com.spt.learningmanage.model.dto.agent.AgentTeamWorkloadRequest;
import com.spt.learningmanage.model.entity.AiAgentRun;
import com.spt.learningmanage.model.vo.agent.AgentCancelVO;
import com.spt.learningmanage.model.vo.agent.AgentRunCreatedVO;
import com.spt.learningmanage.model.vo.agent.AgentRunVO;
import com.spt.learningmanage.service.AgentRunService;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.trace.TraceContext;
import com.spt.learningmanage.utils.UserHolder;
import com.spt.learningmanage.agent.AgentRunStateMachine;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import com.spt.learningmanage.observability.AiMetricsRecorder;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AgentRunServiceImpl implements AgentRunService {
    private final AiAgentRunMapper runMapper;
    private final PermissionService permissionService;
    private final AgentProperties properties;
    private final AgentRunStateMachine stateMachine;
    private AiMetricsRecorder metricsRecorder;

    public AgentRunServiceImpl(AiAgentRunMapper runMapper,
                               PermissionService permissionService,
                               AgentProperties properties,
                               AgentRunStateMachine stateMachine) {
        this.runMapper = runMapper;
        this.permissionService = permissionService;
        this.properties = properties;
        this.stateMachine = stateMachine;
    }

    @Autowired(required = false)
    void setMetricsRecorder(AiMetricsRecorder metricsRecorder) {
        this.metricsRecorder = metricsRecorder;
    }

    @Override
    @Transactional
    public AgentRunCreatedVO submitProjectRisk(AgentProjectRiskRequest request) {
        requireEnabled();
        Long actor = currentUserId();
        permissionService.requireProjectView(actor, request.getProjectId());
        return submit(actor, AgentSceneEnum.PROJECT_RISK, request.getProjectId(), null,
                request.getClientRequestId().trim(), properties.isToolCallingEnabled()
                        ? AgentOrchestrationModeEnum.TOOL_CALLING : AgentOrchestrationModeEnum.FIXED_WORKFLOW);
    }

    @Override
    @Transactional
    public AgentRunCreatedVO submitTeamWorkload(AgentTeamWorkloadRequest request) {
        requireEnabled();
        Long actor = currentUserId();
        permissionService.requireTeamWorkloadAnalyze(actor, request.getTeamId());
        return submit(actor, AgentSceneEnum.TEAM_WORKLOAD, null, request.getTeamId(),
                request.getClientRequestId().trim(), AgentOrchestrationModeEnum.FIXED_WORKFLOW);
    }

    @Override
    public AgentRunVO getRun(String runId) {
        AiAgentRun run = requireOwned(runId, currentUserId());
        return toVO(run);
    }

    @Override
    @Transactional
    public AgentCancelVO cancel(String runId) {
        AiAgentRun run = requireOwned(runId, currentUserId());
        AgentRunStatusEnum status = AgentRunStatusEnum.valueOf(run.getStatus());
        if (status.isTerminal()) {
            throw new BusinessException(ErrorCode.AGENT_RUN_ALREADY_FINISHED);
        }
        stateMachine.requireTransition(status, status == AgentRunStatusEnum.PENDING
                ? AgentRunStatusEnum.CANCELED : AgentRunStatusEnum.CANCELED);
        LocalDateTime now = LocalDateTime.now();
        if (status == AgentRunStatusEnum.PENDING && runMapper.cancelPending(run.getId(), now) == 1) {
            if (metricsRecorder != null) {
                metricsRecorder.recordAgentRun(run.getScene(), AgentRunStatusEnum.CANCELED.name(),
                        run.getOrchestrationMode(), 0L);
            }
            return new AgentCancelVO(run.getRunId(), AgentRunStatusEnum.CANCELED.name(), true);
        }
        runMapper.requestRunningCancellation(run.getId(), now);
        return new AgentCancelVO(run.getRunId(), AgentRunStatusEnum.RUNNING.name(), true);
    }

    private AgentRunCreatedVO submit(Long actor,
                                     AgentSceneEnum scene,
                                     Long projectId,
                                     Long teamId,
                                     String clientRequestId,
                                     AgentOrchestrationModeEnum mode) {
        AiAgentRun existing = findByIdempotency(actor, scene, clientRequestId);
        if (existing != null) {
            return new AgentRunCreatedVO(existing.getRunId(), existing.getStatus());
        }
        long active = runMapper.selectCount(new LambdaQueryWrapper<AiAgentRun>()
                .eq(AiAgentRun::getUserId, actor)
                .in(AiAgentRun::getStatus, AgentRunStatusEnum.PENDING.name(), AgentRunStatusEnum.RUNNING.name()));
        if (active >= properties.getMaxConcurrentRunsPerUser()) {
            throw new BusinessException(ErrorCode.AGENT_CONCURRENCY_LIMIT);
        }
        AiAgentRun run = new AiAgentRun();
        run.setRunId(UUID.randomUUID().toString());
        run.setClientRequestId(clientRequestId);
        run.setScene(scene.name());
        run.setUserId(actor);
        run.setProjectId(projectId);
        run.setTeamId(teamId);
        run.setStatus(AgentRunStatusEnum.PENDING.name());
        run.setOrchestrationMode(mode.name());
        run.setToolCount(0);
        run.setAttemptCount(0);
        run.setTraceId(TraceContext.currentOrCreate());
        LocalDateTime now = LocalDateTime.now();
        run.setCreateTime(now);
        run.setUpdateTime(now);
        try {
            runMapper.insert(run);
        } catch (DuplicateKeyException exception) {
            existing = findByIdempotency(actor, scene, clientRequestId);
            if (existing == null) {
                throw exception;
            }
            run = existing;
        }
        return new AgentRunCreatedVO(run.getRunId(), run.getStatus());
    }

    private AiAgentRun findByIdempotency(Long actor, AgentSceneEnum scene, String clientRequestId) {
        return runMapper.selectOne(new LambdaQueryWrapper<AiAgentRun>()
                .eq(AiAgentRun::getUserId, actor)
                .eq(AiAgentRun::getScene, scene.name())
                .eq(AiAgentRun::getClientRequestId, clientRequestId)
                .last("limit 1"));
    }

    private AiAgentRun requireOwned(String runId, Long actor) {
        if (runId == null || runId.isBlank() || runId.length() > 64) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "runId 不合法");
        }
        AiAgentRun run = runMapper.selectOne(new LambdaQueryWrapper<AiAgentRun>()
                .eq(AiAgentRun::getRunId, runId.trim())
                .eq(AiAgentRun::getUserId, actor)
                .last("limit 1"));
        if (run == null) {
            throw new BusinessException(ErrorCode.AGENT_RUN_NOT_FOUND);
        }
        return run;
    }

    private AgentRunVO toVO(AiAgentRun run) {
        return new AgentRunVO(run.getRunId(), run.getScene(), run.getStatus(), run.getCurrentStep(),
                run.getToolCount() == null ? 0 : run.getToolCount(), properties.getMaxToolCalls(),
                run.getOrchestrationMode(), run.getPartialReason() != null, run.getPartialReason(),
                run.getFailureType(), run.getDraftId(), run.getCreateTime(), run.getStartedAt(), run.getFinishedAt());
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.AGENT_DISABLED);
        }
    }

    private Long currentUserId() {
        Long actor = UserHolder.get();
        if (actor == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return actor;
    }
}
