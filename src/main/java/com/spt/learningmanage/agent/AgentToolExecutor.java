package com.spt.learningmanage.agent;

import com.spt.learningmanage.config.AgentProperties;
import com.spt.learningmanage.constant.AgentToolStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiAgentToolLogMapper;
import com.spt.learningmanage.model.entity.AiAgentRun;
import com.spt.learningmanage.model.entity.AiAgentToolLog;
import com.spt.learningmanage.service.knowledge.KnowledgeHashing;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.spt.learningmanage.observability.AiMetricsRecorder;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Future;

@Component
public class AgentToolExecutor {
    private final AgentToolRegistry registry;
    private final AiAgentToolLogMapper logMapper;
    private final KnowledgeHashing hashing;
    private final AgentProperties properties;
    private final ExecutorService executor;
    private AiMetricsRecorder metricsRecorder;

    public AgentToolExecutor(AgentToolRegistry registry,
                             AiAgentToolLogMapper logMapper,
                             KnowledgeHashing hashing,
                             AgentProperties properties,
                             @Qualifier("agentToolTaskExecutor") ExecutorService executor) {
        this.registry = registry;
        this.logMapper = logMapper;
        this.hashing = hashing;
        this.properties = properties;
        this.executor = executor;
    }

    @Autowired(required = false)
    void setMetricsRecorder(AiMetricsRecorder metricsRecorder) {
        this.metricsRecorder = metricsRecorder;
    }

    public AgentToolExecution execute(AiAgentRun run,
                                      ToolExecutionContext context,
                                      int sequence,
                                      String toolCallId,
                                      String toolName,
                                      String argumentsJson) {
        if (sequence < 1 || sequence > properties.getMaxToolCalls()) {
            throw new BusinessException(ErrorCode.TOOL_CALL_LIMIT_EXCEEDED);
        }
        String normalizedArguments = argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson;
        AiAgentToolLog log = startLog(run, context, sequence, toolCallId, toolName, normalizedArguments);
        long start = System.currentTimeMillis();
        Future<AgentToolExecution> future = null;
        try {
            future = executor.submit(() -> registry.execute(toolName, normalizedArguments, context));
            AgentToolExecution result = future.get(properties.getToolTimeoutSeconds(), TimeUnit.SECONDS);
            if (result.resultJson().length() > properties.getMaxToolOutputChars()) {
                throw new BusinessException(ErrorCode.TOOL_EXECUTION_FAILED, "Tool 输出超过安全上限");
            }
            finish(log, AgentToolStatusEnum.SUCCEEDED, result.resultJson(), null, start);
            return result;
        } catch (TimeoutException exception) {
            if (future != null) {
                future.cancel(true);
            }
            finish(log, AgentToolStatusEnum.TIMED_OUT, null, "TIMEOUT", start);
            throw new BusinessException(ErrorCode.TOOL_EXECUTION_FAILED, "Tool 执行超时");
        } catch (BusinessException exception) {
            finish(log, AgentToolStatusEnum.REJECTED, null, exception.getErrorCode().name(), start);
            throw exception;
        } catch (Exception exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof BusinessException business) {
                finish(log, AgentToolStatusEnum.REJECTED, null, business.getErrorCode().name(), start);
                throw business;
            }
            finish(log, AgentToolStatusEnum.FAILED, null, exception.getClass().getSimpleName(), start);
            throw new BusinessException(ErrorCode.TOOL_EXECUTION_FAILED);
        }
    }

    private AiAgentToolLog startLog(AiAgentRun run,
                                    ToolExecutionContext context,
                                    int sequence,
                                    String toolCallId,
                                    String toolName,
                                    String argumentsJson) {
        AiAgentToolLog log = new AiAgentToolLog();
        log.setRunId(run.getRunId());
        log.setAttemptNo(run.getAttemptCount());
        log.setToolSequence(sequence);
        log.setToolCallId(toolCallId);
        log.setToolName(toolName);
        log.setStatus(AgentToolStatusEnum.RUNNING.name());
        log.setArgumentHash(hashing.sha256(argumentsJson));
        log.setArgumentSummary("metadata-only;chars=" + argumentsJson.length());
        log.setObservedDataVersion(context.dataVersion());
        log.setTraceId(context.traceId());
        log.setStartedAt(LocalDateTime.now());
        logMapper.insert(log);
        return log;
    }

    private void finish(AiAgentToolLog log,
                        AgentToolStatusEnum status,
                        String result,
                        String failureType,
                        long start) {
        log.setStatus(status.name());
        log.setResultHash(result == null ? null : hashing.sha256(result));
        log.setResultSummary(result == null ? null : "metadata-only;chars=" + result.length());
        log.setFailureType(failureType);
        log.setDurationMs(Math.max(System.currentTimeMillis() - start, 0));
        log.setFinishedAt(LocalDateTime.now());
        logMapper.updateById(log);
        if (metricsRecorder != null) {
            metricsRecorder.recordTool(log.getToolName(), status.name(), log.getDurationMs());
        }
    }
}
