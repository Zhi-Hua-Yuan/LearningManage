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
import com.spt.learningmanage.agent.AgentRunStateMachine;

@Service
public class AgentRunFinalizationServiceImpl implements AgentRunFinalizationService {
    private final AiDraftLifecycleService draftLifecycleService;
    private final AgentRunQueueService queueService;
    private final AgentRunStateMachine stateMachine;
    private final com.spt.learningmanage.config.AgentProperties properties;

    public AgentRunFinalizationServiceImpl(AiDraftLifecycleService draftLifecycleService,
                                           AgentRunQueueService queueService,
                                           AgentRunStateMachine stateMachine,
                                           com.spt.learningmanage.config.AgentProperties properties) {
        this.draftLifecycleService = draftLifecycleService;
        this.queueService = queueService;
        this.stateMachine = stateMachine;
        this.properties = properties;
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
    }

    @Override
    @Transactional
    public void completeWithoutDraft(AiAgentRun run,
                                     String terminalStatus,
                                     String failureType,
                                     String safeMessage) {
        stateMachine.requireTransition(AgentRunStatusEnum.RUNNING, AgentRunStatusEnum.valueOf(terminalStatus));
        queueService.complete(run, new AgentRunCompletion(
                terminalStatus, terminalStatus, null, null, null, null,
                failureType, safeMessage, null, null, null));
    }
}
