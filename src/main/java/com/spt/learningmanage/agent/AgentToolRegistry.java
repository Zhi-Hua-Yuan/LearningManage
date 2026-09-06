package com.spt.learningmanage.agent;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AgentToolRegistry {
    private final Map<String, AgentTool<?>> tools;
    private final ObjectMapper strictMapper;
    private final Validator validator;

    public AgentToolRegistry(List<AgentTool<?>> registeredTools,
                             ObjectMapper objectMapper,
                             Validator validator) {
        Map<String, AgentTool<?>> values = new LinkedHashMap<>();
        for (AgentTool<?> tool : registeredTools) {
            AgentTool<?> previous = values.putIfAbsent(tool.name(), tool);
            if (previous != null) {
                throw new IllegalStateException("Agent Tool 名称重复: " + tool.name());
            }
        }
        this.tools = Map.copyOf(values);
        this.strictMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        this.validator = validator;
    }

    public Set<String> namesFor(ToolExecutionContext context) {
        return tools.values().stream()
                .filter(tool -> tool.allowedScenes().contains(context.scene()))
                .map(AgentTool::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public AgentToolExecution execute(String name, String argumentsJson, ToolExecutionContext context) {
        AgentTool<?> tool = tools.get(name);
        if (tool == null || !tool.allowedScenes().contains(context.scene())) {
            throw new BusinessException(ErrorCode.TOOL_NOT_ALLOWED);
        }
        try {
            Object arguments = strictMapper.readValue(
                    argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson,
                    tool.argumentType());
            Set<ConstraintViolation<Object>> violations = validator.validate(arguments);
            if (!violations.isEmpty()) {
                throw new BusinessException(ErrorCode.TOOL_ARGUMENT_INVALID,
                        violations.iterator().next().getMessage());
            }
            Object result = executeTyped(tool, context, arguments);
            return new AgentToolExecution(name, result, strictMapper.writeValueAsString(result));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.TOOL_ARGUMENT_INVALID, "Tool 参数或结果格式不合法");
        }
    }

    @SuppressWarnings("unchecked")
    private <A> Object executeTyped(AgentTool<?> raw,
                                    ToolExecutionContext context,
                                    Object arguments) {
        AgentTool<A> tool = (AgentTool<A>) raw;
        return tool.execute(context, (A) arguments);
    }
}

