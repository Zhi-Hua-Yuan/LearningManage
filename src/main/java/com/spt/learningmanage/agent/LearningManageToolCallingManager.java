package com.spt.learningmanage.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.config.AgentProperties;
import com.spt.learningmanage.constant.AgentSceneEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiAgentToolLogMapper;
import com.spt.learningmanage.model.dto.ai.chat.AiFunctionDefinition;
import com.spt.learningmanage.model.dto.ai.chat.AiToolDefinition;
import com.spt.learningmanage.model.entity.AiAgentRun;
import com.spt.learningmanage.service.agent.AgentRunQueueService;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Application-level Tool Calling boundary.
 *
 * <p>This class deliberately does not implement or register Spring AI's
 * global {@code ToolCallingManager}. Spring AI receives request-scoped tool
 * definitions for serialization only; execution remains here so policy,
 * authorization, fencing, timeout and audit rules cannot be bypassed by
 * framework auto-configuration.</p>
 */
@Component
public class LearningManageToolCallingManager {
    private final AgentToolRegistry registry;
    private final AgentToolPolicy policy;
    private final AgentToolExecutor executor;
    private final AgentProperties properties;
    private final AgentRunQueueService queueService;
    private final AiAgentToolLogMapper toolLogMapper;
    private final ObjectMapper objectMapper;

    public LearningManageToolCallingManager(AgentToolRegistry registry,
                                            AgentToolPolicy policy,
                                            AgentToolExecutor executor,
                                            AgentProperties properties,
                                            AgentRunQueueService queueService,
                                            AiAgentToolLogMapper toolLogMapper,
                                            ObjectMapper objectMapper) {
        this.registry = registry;
        this.policy = policy;
        this.executor = executor;
        this.properties = properties;
        this.queueService = queueService;
        this.toolLogMapper = toolLogMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * Builds the provider-neutral definitions for one run.  RAG availability
     * is a run policy input, so retrieveProjectHistory is never merely hidden
     * in the prompt while remaining callable.
     */
    public List<AiToolDefinition> definitionsFor(AgentSceneEnum scene, boolean ragEnabled) {
        Set<String> available = availableNames(scene, ragEnabled);
        return registry.toolsFor(scene).stream()
                .filter(tool -> available.contains(tool.name()))
                .map(tool -> AiToolDefinition.function(new AiFunctionDefinition(
                        tool.name(), tool.description(), schema(tool.argumentType()))))
                .toList();
    }

    /**
     * Executes one model-requested or fixed-workflow Tool through the existing
     * audited executor.  The caller supplies names already completed in this
     * execution so duplicate calls are rejected before any database work.
     */
    public AgentToolExecution execute(AiAgentRun run,
                                      ToolExecutionContext context,
                                      Set<String> alreadyCalled,
                                      String toolCallId,
                                      String toolName,
                                      String argumentsJson,
                                      boolean ragEnabled) {
        Set<String> available = availableNames(context.scene(), ragEnabled);
        if (!available.contains(toolName)) {
            throw new BusinessException(ErrorCode.TOOL_NOT_ALLOWED);
        }
        policy.requireCallAllowed(context.scene(), alreadyCalled, toolName);
        checkCanceled(run);

        int persistedSequence = toolLogMapper.selectMaxSequence(run.getRunId(), run.getAttemptCount());
        int nextSequence = Math.max(alreadyCalled.size() + 1, persistedSequence + 1);
        if (nextSequence > properties.getMaxToolCalls()) {
            throw new BusinessException(ErrorCode.TOOL_CALL_LIMIT_EXCEEDED);
        }
        queueService.updateProgress(run, "TOOL:" + toolName, nextSequence - 1, context.dataVersion());
        AgentToolExecution value = executor.execute(run, context, nextSequence, toolCallId,
                toolName, argumentsJson);
        queueService.updateProgress(run, "TOOL_COMPLETED:" + toolName, nextSequence,
                context.dataVersion());
        return value;
    }

    public Set<String> availableNames(AgentSceneEnum scene, boolean ragEnabled) {
        Set<String> allowed = new LinkedHashSet<>(policy.allowed(scene));
        if (!ragEnabled) {
            allowed.remove("retrieveProjectHistory");
        }
        Set<String> registered = registry.toolsFor(scene).stream()
                .map(AgentTool::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        allowed.retainAll(registered);
        return Set.copyOf(allowed);
    }

    public boolean hasRequired(AgentSceneEnum scene, Set<String> called) {
        return policy.hasRequired(scene, called);
    }

    public void checkCanceled(AiAgentRun run) {
        if (queueService.cancellationRequested(run.getRunId(), run.getExecutionToken())) {
            throw new BusinessException(ErrorCode.AGENT_CANCELED);
        }
        if (!queueService.heartbeat(run)) {
            throw new BusinessException(ErrorCode.AGENT_WORKER_LOST);
        }
    }

    private JsonNode schema(Class<?> argumentType) {
        try {
            return objectMapper.readTree(JsonSchemaGenerator.generateForType(argumentType));
        } catch (Exception exception) {
            throw new IllegalStateException("无法为 Agent Tool 生成参数 Schema: " + argumentType.getName(), exception);
        }
    }

}
