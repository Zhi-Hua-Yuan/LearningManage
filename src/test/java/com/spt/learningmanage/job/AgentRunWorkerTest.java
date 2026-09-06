package com.spt.learningmanage.job;

import com.spt.learningmanage.agent.AgentOrchestrationResult;
import com.spt.learningmanage.agent.AgentOrchestrator;
import com.spt.learningmanage.config.AgentProperties;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.entity.AiAgentRun;
import com.spt.learningmanage.service.agent.AgentRunFinalizationService;
import com.spt.learningmanage.service.agent.AgentRunQueueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

class AgentRunWorkerTest {
    private final AgentOrchestrator orchestrator = mock(AgentOrchestrator.class);
    private final AgentRunFinalizationService finalization = mock(AgentRunFinalizationService.class);
    private final AgentRunQueueService queue = mock(AgentRunQueueService.class);
    private final ExecutorService orchestrationExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    private final AgentRunWorker worker = new AgentRunWorker(
            orchestrator, finalization, new AgentProperties(), queue,
            orchestrationExecutor, heartbeatExecutor);

    @AfterEach
    void shutdownExecutors() {
        orchestrationExecutor.shutdownNow();
        heartbeatExecutor.shutdownNow();
    }

    @Test
    void successfulAnalysisFinalizesWithDraft() {
        AiAgentRun run = run();
        AgentOrchestrationResult result = new AgentOrchestrationResult(
                "{}", 1L, 2, false, null, 3L, "model", "prompt", 1);
        when(orchestrator.orchestrate(run)).thenReturn(result);

        worker.process(run);

        verify(finalization).completeWithDraft(run, result);
    }

    @Test
    void cancellationProducesCanceledTerminalWithoutDraft() {
        AiAgentRun run = run();
        when(orchestrator.orchestrate(run)).thenThrow(new BusinessException(ErrorCode.AGENT_CANCELED));

        worker.process(run);

        verify(finalization).completeWithoutDraft(eq(run), eq("CANCELED"),
                eq("AGENT_CANCELED"), org.mockito.ArgumentMatchers.anyString());
    }

    private AiAgentRun run() {
        AiAgentRun run = new AiAgentRun();
        run.setRunId("run-1");
        run.setStatus("RUNNING");
        run.setExecutionToken("token");
        return run;
    }
}
