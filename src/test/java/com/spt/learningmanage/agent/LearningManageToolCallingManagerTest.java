package com.spt.learningmanage.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.config.AgentProperties;
import com.spt.learningmanage.constant.AgentSceneEnum;
import com.spt.learningmanage.mapper.AiAgentToolLogMapper;
import com.spt.learningmanage.model.entity.AiAgentRun;
import com.spt.learningmanage.service.agent.AgentRunQueueService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningManageToolCallingManagerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentToolExecutor executor = mock(AgentToolExecutor.class);
    private final AgentRunQueueService queue = mock(AgentRunQueueService.class);
    private final AiAgentToolLogMapper logMapper = mock(AiAgentToolLogMapper.class);
    private final AgentProperties properties = new AgentProperties();
    private final LearningManageToolCallingManager manager = new LearningManageToolCallingManager(
            registry(), new AgentToolPolicy(properties), executor, properties, queue, logMapper, objectMapper);

    @Test
    void definitionsAreStableAndRagHistoryIsExplicitlyFiltered() throws Exception {
        var enabled = manager.definitionsFor(AgentSceneEnum.PROJECT_RISK, true);
        assertEquals(List.of("queryTaskStats", "retrieveProjectHistory"),
                enabled.stream().map(value -> value.function().name()).toList());
        JsonNode schema = enabled.get(0).function().parameters();
        assertEquals("object", schema.path("type").asText());
        assertFalse(schema.toString().contains("projectId"));

        var disabled = manager.definitionsFor(AgentSceneEnum.PROJECT_RISK, false);
        assertEquals(List.of("queryTaskStats"),
                disabled.stream().map(value -> value.function().name()).toList());
        assertFalse(manager.availableNames(AgentSceneEnum.PROJECT_RISK, false)
                .contains("retrieveProjectHistory"));
    }

    @Test
    void duplicateAndUnavailableToolsAreRejectedBeforeExecution() {
        AiAgentRun run = run();
        when(queue.cancellationRequested(anyString(), anyString())).thenReturn(false);
        when(queue.heartbeat(run)).thenReturn(true);
        when(logMapper.selectMaxSequence(anyString(), anyInt())).thenReturn(0);

        assertThrows(com.spt.learningmanage.exception.BusinessException.class,
                () -> manager.execute(run, context(), Set.of("queryTaskStats"), "call-2",
                        "queryTaskStats", "{}", true));
        assertThrows(com.spt.learningmanage.exception.BusinessException.class,
                () -> manager.execute(run, context(), Set.of(), "call-3",
                        "retrieveProjectHistory", "{}", false));
        verify(executor, never()).execute(any(), any(), anyInt(), any(), anyString(), anyString());
    }

    @Test
    void executionUsesRunContextAndAuditedExecutor() {
        AiAgentRun run = run();
        when(queue.cancellationRequested(anyString(), anyString())).thenReturn(false);
        when(queue.heartbeat(run)).thenReturn(true);
        when(logMapper.selectMaxSequence(anyString(), anyInt())).thenReturn(0);
        when(executor.execute(any(), any(), anyInt(), anyString(), anyString(), anyString()))
                .thenReturn(new AgentToolExecution("queryTaskStats", "ok", "\"ok\""));

        AgentToolExecution result = manager.execute(run, context(), Set.of(), "call-1",
                "queryTaskStats", "{}", false);

        assertEquals("queryTaskStats", result.toolName());
        verify(executor).execute(run, context(), 1, "call-1", "queryTaskStats", "{}");
        verify(queue).updateProgress(run, "TOOL:queryTaskStats", 0, 7L);
        verify(queue).updateProgress(run, "TOOL_COMPLETED:queryTaskStats", 1, 7L);
    }

    private AgentToolRegistry registry() {
        return new AgentToolRegistry(List.of(new StatsTool(), new HistoryTool()), objectMapper,
                Validation.buildDefaultValidatorFactory().getValidator());
    }

    private AiAgentRun run() {
        AiAgentRun run = new AiAgentRun();
        run.setRunId("run-1");
        run.setAttemptCount(1);
        run.setExecutionToken("token-1");
        return run;
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext(1L, "run-1", AgentSceneEnum.PROJECT_RISK,
                2L, null, null, "trace-1", 1, "token-1", 7L);
    }

    private static class StatsTool implements AgentTool<EmptyToolArguments> {
        @Override public String name() { return "queryTaskStats"; }
        @Override public String description() { return "查询当前项目任务统计"; }
        @Override public Set<AgentSceneEnum> allowedScenes() { return Set.of(AgentSceneEnum.PROJECT_RISK); }
        @Override public Class<EmptyToolArguments> argumentType() { return EmptyToolArguments.class; }
        @Override public Object execute(ToolExecutionContext context, EmptyToolArguments arguments) { return "ok"; }
    }

    private static class HistoryTool implements AgentTool<ProjectHistoryArguments> {
        @Override public String name() { return "retrieveProjectHistory"; }
        @Override public String description() { return "检索当前项目历史证据"; }
        @Override public Set<AgentSceneEnum> allowedScenes() { return Set.of(AgentSceneEnum.PROJECT_RISK); }
        @Override public Class<ProjectHistoryArguments> argumentType() { return ProjectHistoryArguments.class; }
        @Override public Object execute(ToolExecutionContext context, ProjectHistoryArguments arguments) { return "ok"; }
    }
}
