package com.spt.learningmanage.service.impl.agent;

import com.spt.learningmanage.agent.AgentOrchestrationResult;
import com.spt.learningmanage.constant.AgentRunStatusEnum;
import com.spt.learningmanage.constant.AgentSceneEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.dto.ai.draft.AiDraftCreateCommand;
import com.spt.learningmanage.model.entity.AiAgentRun;
import com.spt.learningmanage.model.entity.AiDraft;
import com.spt.learningmanage.service.agent.AgentRunCompletion;
import com.spt.learningmanage.service.agent.AgentRunFinalizationService;
import com.spt.learningmanage.service.agent.AgentRunQueueService;
import com.spt.learningmanage.service.ai.support.AiDraftLifecycleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import com.spt.learningmanage.observability.AiMetricsRecorder;
import java.time.Duration;
import java.time.LocalDateTime;
import com.spt.learningmanage.agent.AgentRunStateMachine;

@Service
public class AgentRunFinalizationServiceImpl implements AgentRunFinalizationService {
    private final AiDraftLifecycleService draftLifecycleService;
    private final AgentRunQueueService queueService;
    private final AgentRunStateMachine stateMachine;
    private final com.spt.learningmanage.config.AgentProperties properties;
    private AiMetricsRecorder metricsRecorder;

    public AgentRunFinalizationServiceImpl(AiDraftLifecycleService draftLifecycleService,
                                           AgentRunQueueService queueService,
                                           AgentRunStateMachine stateMachine,
                                           com.spt.learningmanage.config.AgentProperties properties) {
        this.draftLifecycleService = draftLifecycleService;
        this.queueService = queueService;
        this.stateMachine = stateMachine;
        this.properties = properties;
    }

    @Autowired(required = false)
    void setMetricsRecorder(AiMetricsRecorder metricsRecorder) {
        this.metricsRecorder = metricsRecorder;
    }

    @Override
    @Transactional
    public void completeWithDraft(AiAgentRun run, AgentOrchestrationResult result) {
        AgentSceneEnum scene = AgentSceneEnum.valueOf(run.getScene());
        AiDraft draft = draftLifecycleService.createDraft(new AiDraftCreateCommand(
                run.getUserId(), scene.getDraftScene(), result.payloadJson(),
                draftLifecycleService.buildInputHash(result.payloadJson()), 1, run.getTraceId(),
                properties.getDraftTtlMinutes()));
        String terminal = result.partial() ? AgentRunStatusEnum.PARTIAL.name()
                : AgentRunStatusEnum.SUCCEEDED.name();
        stateMachine.requireTransition(AgentRunStatusEnum.RUNNING, AgentRunStatusEnum.valueOf(terminal));
        boolean completed = queueService.complete(run, new AgentRunCompletion(
                terminal, "DRAFT_READY", result.dataVersion(), draft.getDraftId(), result.aiCallLogId(),
                result.partialReason(), null, null, result.model(), result.promptCode(), result.promptVersion()));
        if (!completed) {
            throw new BusinessException(ErrorCode.AGENT_WORKER_LOST, "Agent Run 终态写入冲突");
        }
        record(run, terminal, run.getOrchestrationMode());
    }

    @Override
    @Transactional
    public void completeWithoutDraft(AiAgentRun run,
                                     String terminalStatus,
                                     String failureType,
                                     String safeMessage) {
        stateMachine.requireTransition(AgentRunStatusEnum.RUNNING, AgentRunStatusEnum.valueOf(terminalStatus));
        boolean completed = queueService.complete(run, new AgentRunCompletion(
                terminalStatus, terminalStatus, null, null, null, null,
                failureType, safeMessage, null, null, null));
        if (completed) {
            record(run, terminalStatus, run.getOrchestrationMode());
        }
    }

    private void record(AiAgentRun run, String status, String mode) {
        if (metricsRecorder == null) {
            return;
        }
        LocalDateTime started = run.getStartedAt() == null ? run.getCreateTime() : run.getStartedAt();
        long duration = started == null ? 0L
                : Math.max(Duration.between(started, LocalDateTime.now()).toMillis(), 0L);
        metricsRecorder.recordAgentRun(run.getScene(), status, mode, duration);
    }
}
