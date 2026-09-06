package com.spt.learningmanage.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.constant.AgentSceneEnum;
import com.spt.learningmanage.exception.BusinessException;
import jakarta.validation.Validation;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentToolRegistryTest {
    private final AgentToolRegistry registry = new AgentToolRegistry(
            List.of(new TestTool()), new ObjectMapper(),
            Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void executesOnlyRegisteredToolInAllowedScene() {
        AgentToolExecution result = registry.execute("safeTool", "{\"query\":\"ok\"}", context());
        assertEquals("safeTool", result.toolName());
        assertEquals("ok", result.result());
    }

    @Test
    void rejectsUnknownFieldsAndUnregisteredTools() {
        assertThrows(BusinessException.class,
                () -> registry.execute("safeTool", "{\"projectId\":999}", context()));
        assertThrows(BusinessException.class,
                () -> registry.execute("deleteProject", "{}", context()));
    }

    @Test
    void rejectsToolOutsideScenePolicy() {
        ToolExecutionContext teamContext = new ToolExecutionContext(1L, "run", AgentSceneEnum.TEAM_WORKLOAD,
                null, 2L, null, "trace", 1, "token", 0L);
        assertThrows(BusinessException.class,
                () -> registry.execute("safeTool", "{}", teamContext));
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext(1L, "run", AgentSceneEnum.PROJECT_RISK,
                2L, null, null, "trace", 1, "token", 0L);
    }

    private record Args(@Size(max = 10) String query) {
    }

    private static class TestTool implements AgentTool<Args> {
        @Override public String name() { return "safeTool"; }
        @Override public Set<AgentSceneEnum> allowedScenes() { return Set.of(AgentSceneEnum.PROJECT_RISK); }
        @Override public Class<Args> argumentType() { return Args.class; }
        @Override public Object execute(ToolExecutionContext context, Args arguments) { return arguments.query(); }
    }
}
