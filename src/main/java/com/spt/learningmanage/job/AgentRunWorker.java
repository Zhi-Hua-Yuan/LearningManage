package com.spt.learningmanage.job;

import com.spt.learningmanage.agent.AgentOrchestrationResult;
import com.spt.learningmanage.agent.AgentOrchestrator;
import com.spt.learningmanage.config.AgentProperties;
import com.spt.learningmanage.constant.AgentRunStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.entity.AiAgentRun;
import com.spt.learningmanage.service.agent.AgentRunFinalizationService;
import com.spt.learningmanage.service.agent.AgentRunQueueService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Component
public class AgentRunWorker {
    private final AgentOrchestrator orchestrator;
    private final AgentRunFinalizationService finalizationService;
    private final AgentProperties properties;
    private final AgentRunQueueService queueService;
    private final ExecutorService orchestrationExecutor;
    private final ScheduledExecutorService heartbeatExecutor;

    public AgentRunWorker(AgentOrchestrator orchestrator,
                          AgentRunFinalizationService finalizationService,
                          AgentProperties properties,
                          AgentRunQueueService queueService,
                          @Qualifier("agentOrchestrationTaskExecutor") ExecutorService orchestrationExecutor,
                          @Qualifier("agentHeartbeatTaskExecutor") ScheduledExecutorService heartbeatExecutor) {
        this.orchestrator = orchestrator;
        this.finalizationService = finalizationService;
        this.properties = properties;
        this.queueService = queueService;
        this.orchestrationExecutor = orchestrationExecutor;
        this.heartbeatExecutor = heartbeatExecutor;
    }

    public void process(AiAgentRun run) {
        ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(
                () -> queueService.heartbeat(run), properties.getHeartbeatSeconds(),
                properties.getHeartbeatSeconds(), TimeUnit.SECONDS);
        Future<AgentOrchestrationResult> future = orchestrationExecutor.submit(() -> orchestrator.orchestrate(run));
        try {
            AgentOrchestrationResult result = future.get(properties.getOverallTimeoutSeconds(), TimeUnit.SECONDS);
            finalizationService.completeWithDraft(run, result);
        } catch (TimeoutException exception) {
            future.cancel(true);
            finalizationService.completeWithoutDraft(run, AgentRunStatusEnum.TIMED_OUT.name(),
                    ErrorCode.AGENT_TIMEOUT.name(), "Agent 运行超过整体时限");
        } catch (ExecutionException exception) {
            handleFailure(run, exception.getCause());
        } catch (BusinessException exception) {
            handleFailure(run, exception);
        } catch (Exception exception) {
            future.cancel(true);
            handleFailure(run, exception);
        } finally {
            heartbeat.cancel(false);
        }
    }

    private void handleFailure(AiAgentRun run, Throwable throwable) {
        if (throwable instanceof BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.AGENT_WORKER_LOST) {
                return;
            }
            String terminal = exception.getErrorCode() == ErrorCode.AGENT_CANCELED
                    ? AgentRunStatusEnum.CANCELED.name() : AgentRunStatusEnum.FAILED.name();
            finalizationService.completeWithoutDraft(run, terminal,
                    exception.getErrorCode().name(), exception.getMessage());
        } else {
            finalizationService.completeWithoutDraft(run, AgentRunStatusEnum.FAILED.name(),
                    "INTERNAL", "Agent 运行失败");
        }
    }
}
