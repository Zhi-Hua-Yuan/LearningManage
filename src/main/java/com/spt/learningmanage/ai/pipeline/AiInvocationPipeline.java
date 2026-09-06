package com.spt.learningmanage.ai.pipeline;

import cn.hutool.json.JSONUtil;
import com.spt.learningmanage.constant.AiCallFailureTypeEnum;
import com.spt.learningmanage.constant.AiCallLogStatusEnum;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.exception.AiInvocationException;
import com.spt.learningmanage.exception.AiResponseProcessingException;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.model.dto.ai.AiCallLogCompletionCommand;
import com.spt.learningmanage.model.dto.ai.AiCallLogCreateCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiChatCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiChatMessage;
import com.spt.learningmanage.model.dto.ai.chat.AiChatResult;
import com.spt.learningmanage.model.dto.ai.chat.AiToolChoice;
import com.spt.learningmanage.prompt.AiPromptTemplate;
import com.spt.learningmanage.prompt.PromptTemplateResolver;
import com.spt.learningmanage.service.AiCallLogService;
import com.spt.learningmanage.service.AiModelClient;
import com.spt.learningmanage.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一编排 Prompt、Chat 模型调用、响应处理、规则降级和调用日志终态。
 */
@Component
public class AiInvocationPipeline {

    private static final Logger log = LoggerFactory.getLogger(AiInvocationPipeline.class);
    private static final String RAW_PROMPT_CODE = "legacy-chat";
    private static final String RAW_PROMPT_SOURCE = "runtime";

    private final PromptTemplateResolver promptTemplateResolver;
    private final AiModelClient aiModelClient;
    private final AiCallLogService aiCallLogService;

    public AiInvocationPipeline(PromptTemplateResolver promptTemplateResolver,
                                AiModelClient aiModelClient,
                                AiCallLogService aiCallLogService) {
        this.promptTemplateResolver = promptTemplateResolver;
        this.aiModelClient = aiModelClient;
        this.aiCallLogService = aiCallLogService;
    }

    public <T> AiExecutionResult<T> execute(AiExecutionCommand command,
                                            AiResponseProcessor<T> responseProcessor) {
        return execute(command, responseProcessor, null);
    }

    public <T> AiExecutionResult<T> execute(AiExecutionCommand command,
                                            AiResponseProcessor<T> responseProcessor,
                                            AiFallback<T> fallback) {
        if (command == null) {
            throw new IllegalArgumentException("AI 执行命令不能为空");
        }
        AiPromptTemplate template = promptTemplateResolver.resolve(command.promptCode());
        return executeResolved(new ResolvedExecution(
                command.userId(), command.modelName(), template.scene(), template.code(),
                template.templateId(), template.version(), template.source().getCode(),
                template.systemPrompt(), command.userPrompt(), command.parseFailureMessage(), command.traceId(),
                command.contentLoggingPolicy(), command.requestLogSummary(), null, null
        ), responseProcessor, fallback);
    }

    /**
     * 受限兼容入口：仅供既有内部通用 chat 使用，不允许 Tool。
     */
    public <T> AiExecutionResult<T> executeRaw(AiRawExecutionCommand command,
                                               AiResponseProcessor<T> responseProcessor) {
        if (command == null) {
            throw new IllegalArgumentException("AI 原始执行命令不能为空");
        }
        return executeResolved(new ResolvedExecution(
                command.userId(), command.modelName(), RAW_PROMPT_CODE, RAW_PROMPT_CODE,
                null, null, RAW_PROMPT_SOURCE, command.systemPrompt(), command.userPrompt(),
                command.parseFailureMessage(), command.traceId(), AiContentLoggingPolicy.DEFAULT, null,
                null, null
        ), responseProcessor, null);
    }

    /**
     * Execute one Tool-capable Agent model round. Request and response bodies
     * are never persisted; each round is correlated to its durable Agent Run.
     */
    public AiExecutionResult<AiChatResult> executeChatRound(AiChatRoundExecutionCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Agent 模型轮次不能为空");
        }
        AiPromptTemplate template = promptTemplateResolver.resolve(command.promptCode());
        ResolvedExecution execution = new ResolvedExecution(
                command.userId(), command.modelName(), template.scene(), template.code(),
                template.templateId(), template.version(), template.source().getCode(),
                template.systemPrompt(), "agent-round", "Agent 模型轮次响应异常", command.traceId(),
                AiContentLoggingPolicy.METADATA_ONLY, command.requestLogSummary(),
                command.agentRunId(), command.agentRoundNo());
        String traceId = resolveTraceId(command.traceId());
        long startTime = System.currentTimeMillis();
        Long callLogId = createCallLogSafely(execution, traceId);
        List<AiChatMessage> messages = new ArrayList<>();
        messages.add(AiChatMessage.system(template.systemPrompt()));
        messages.addAll(command.messages());
        try {
            AiChatResult chatResult = aiModelClient.chat(new AiChatCommand(
                    command.modelName(), messages, command.tools(), command.toolChoice(),
                    command.temperature(), command.maxOutputTokens()));
            if ((chatResult.content() == null || chatResult.content().isBlank())
                    && chatResult.toolCalls().isEmpty()) {
                throw new AiInvocationException(
                        AiFailureTypeEnum.INVALID_RESPONSE, command.modelName(), 0,
                        "AI 返回结果格式异常，请重试", "Agent 模型轮次没有文本或 Tool Call", null);
            }
            long duration = elapsedSince(startTime);
            completeSafely(completion(callLogId, AiCallLogStatusEnum.SUCCESS, null, null,
                    duration, execution, chatResult, traceId, null, false, null));
            return result(chatResult, callLogId, execution, chatResult, duration,
                    false, null, traceId);
        } catch (AiInvocationException exception) {
            return handleInvocationFailure(execution, null, traceId, callLogId,
                    startTime, null, exception);
        } catch (Exception exception) {
            AiInvocationException wrapped = new AiInvocationException(
                    AiFailureTypeEnum.INTERNAL_ERROR, command.modelName(), 0,
                    "AI 服务暂时不可用，请稍后重试", "Agent 模型轮次异常", exception);
            return handleInvocationFailure(execution, null, traceId, callLogId,
                    startTime, null, wrapped);
        }
    }

    private <T> AiExecutionResult<T> executeResolved(ResolvedExecution execution,
                                                     AiResponseProcessor<T> responseProcessor,
                                                     AiFallback<T> fallback) {
        if (responseProcessor == null) {
            throw new IllegalArgumentException("AI 响应处理器不能为空");
        }

        String traceId = resolveTraceId(execution.traceId());
        long startTime = System.currentTimeMillis();
        Long callLogId = createCallLogSafely(execution, traceId);
        AiChatResult chatResult;

        try {
            chatResult = aiModelClient.chat(new AiChatCommand(
                    execution.modelName(),
                    List.of(AiChatMessage.system(execution.systemPrompt()), AiChatMessage.user(execution.userPrompt())),
                    List.of(), AiToolChoice.none(), null, null
            ));
        } catch (AiInvocationException exception) {
            return handleInvocationFailure(execution, fallback, traceId, callLogId, startTime, null, exception);
        } catch (Exception exception) {
            AiInvocationException wrapped = new AiInvocationException(
                    AiFailureTypeEnum.INTERNAL_ERROR, execution.modelName(), 0,
                    "AI 服务暂时不可用，请稍后重试",
                    "AI 调用发生未分类异常：model=" + execution.modelName(), exception
            );
            return handleInvocationFailure(execution, fallback, traceId, callLogId, startTime, null, wrapped);
        }

        try {
            validateTextResult(chatResult, execution.modelName());
        } catch (AiInvocationException exception) {
            return handleInvocationFailure(execution, fallback, traceId, callLogId, startTime, chatResult, exception);
        }

        try {
            T data = responseProcessor.process(chatResult.content());
            if (data == null) {
                throw new IllegalStateException("AI 响应处理结果不能为空");
            }
            long duration = elapsedSince(startTime);
            completeSafely(completion(callLogId, AiCallLogStatusEnum.SUCCESS,
                    chatResult.content(), null, duration, execution, chatResult, traceId,
                    null, false, null));
            return result(data, callLogId, execution, chatResult, duration, false, null, traceId);
        } catch (Exception exception) {
            AiResponseProcessingException processingException = normalizeProcessingFailure(execution, exception);
            return handleProcessingFailure(execution, fallback, traceId, callLogId, startTime,
                    chatResult, processingException);
        }
    }

    private <T> AiExecutionResult<T> handleInvocationFailure(ResolvedExecution execution,
                                                              AiFallback<T> fallback,
                                                              String traceId,
                                                              Long callLogId,
                                                              long startTime,
                                                              AiChatResult chatResult,
                                                              AiInvocationException exception) {
        AiCallFailureTypeEnum failureType = mapFailureType(exception);
        AiChatResult invocationMetadata = chatResult == null
                ? failureMetadata(execution, exception)
                : chatResult;
        if (fallback == null) {
            long duration = elapsedSince(startTime);
            completeSafely(completion(callLogId, statusFor(failureType), null, exception.getSafeMessage(),
                    duration, execution, invocationMetadata, traceId, failureType, false, null));
            throw exception;
        }

        T fallbackData = applyFallbackOrCompleteFailure(fallback, callLogId, failureType,
                exception.getSafeMessage(), exception, startTime, execution, invocationMetadata, traceId);
        long duration = elapsedSince(startTime);
        String degradationReason = failureType.name() + ": " + exception.getSafeMessage();
        completeSafely(completion(callLogId, AiCallLogStatusEnum.SUCCESS, null, exception.getSafeMessage(),
                duration, execution, invocationMetadata, traceId, failureType, true, degradationReason));
        return result(fallbackData, callLogId, execution, invocationMetadata, duration,
                true, degradationReason, traceId);
    }

    private <T> AiExecutionResult<T> handleProcessingFailure(ResolvedExecution execution,
                                                              AiFallback<T> fallback,
                                                              String traceId,
                                                              Long callLogId,
                                                              long startTime,
                                                              AiChatResult chatResult,
                                                              AiResponseProcessingException exception) {
        AiCallFailureTypeEnum failureType = exception.getFailureType();
        if (fallback == null) {
            long duration = elapsedSince(startTime);
            completeSafely(completion(callLogId, AiCallLogStatusEnum.PARSE_FAILED,
                    chatResult.content(), exception.getSafeMessage(), duration,
                    execution, chatResult, traceId, failureType, false, null));
            throw exception;
        }

        T fallbackData = applyFallbackOrCompleteFailure(fallback, callLogId, failureType,
                exception.getSafeMessage(), exception, startTime, execution, chatResult, traceId);
        long duration = elapsedSince(startTime);
        String degradationReason = failureType.name() + ": " + exception.getSafeMessage();
        completeSafely(completion(callLogId, AiCallLogStatusEnum.SUCCESS,
                chatResult.content(), exception.getSafeMessage(), duration,
                execution, chatResult, traceId, failureType, true, degradationReason));
        return result(fallbackData, callLogId, execution, chatResult, duration,
                true, degradationReason, traceId);
    }

    private <T> T applyFallback(AiFallback<T> fallback,
                                Long callLogId,
                                AiCallFailureTypeEnum failureType,
                                String safeMessage,
                                Throwable cause) {
        T data = fallback.apply(new AiPipelineFailure(callLogId, failureType, safeMessage, cause));
        if (data == null) {
            throw new IllegalStateException("AI 规则降级结果不能为空");
        }
        return data;
    }

    private <T> T applyFallbackOrCompleteFailure(AiFallback<T> fallback,
                                                  Long callLogId,
                                                  AiCallFailureTypeEnum failureType,
                                                  String safeMessage,
                                                  Throwable cause,
                                                  long startTime,
                                                  ResolvedExecution execution,
                                                  AiChatResult chatResult,
                                                  String traceId) {
        try {
            return applyFallback(fallback, callLogId, failureType, safeMessage, cause);
        } catch (RuntimeException fallbackException) {
            long duration = elapsedSince(startTime);
            completeSafely(completion(callLogId, AiCallLogStatusEnum.FAILED,
                    chatResult == null ? null : chatResult.content(), "AI 规则降级执行失败",
                    duration, execution, chatResult, traceId, AiCallFailureTypeEnum.INTERNAL, false, null));
            if (fallbackException != cause) {
                fallbackException.addSuppressed(cause);
            }
            throw fallbackException;
        }
    }

    private void validateTextResult(AiChatResult result, String requestedModel) {
        if (result == null || result.content() == null || result.content().isBlank()) {
            String actualModel = result == null ? requestedModel : normalizedModel(result.actualModel(), requestedModel);
            int retryCount = result == null || result.retryCount() == null ? 0 : Math.max(result.retryCount(), 0);
            throw new AiInvocationException(
                    AiFailureTypeEnum.INVALID_RESPONSE, actualModel, retryCount,
                    "AI 返回结果格式异常，请重试",
                    result != null && !result.toolCalls().isEmpty()
                            ? "文本调用不接受仅包含 Tool Call 的响应" : "文本调用返回空响应",
                    null
            );
        }
    }

    private AiResponseProcessingException normalizeProcessingFailure(ResolvedExecution execution,
                                                                      Exception exception) {
        if (exception instanceof AiResponseProcessingException processingException) {
            return processingException;
        }
        if (exception instanceof BusinessException) {
            return AiResponseProcessingException.businessValidation(execution.parseFailureMessage(), exception);
        }
        return AiResponseProcessingException.parse(execution.parseFailureMessage(), exception);
    }

    private Long createCallLogSafely(ResolvedExecution execution, String traceId) {
        if (execution.userId() == null) {
            return null;
        }
        try {
            return aiCallLogService.createRunningLog(new AiCallLogCreateCommand(
                    execution.userId(), execution.scene(), execution.modelName(), execution.promptCode(),
                    execution.promptTemplateId(), execution.promptVersion(), execution.promptSource(),
                    requestLogText(execution), 0, traceId,
                    execution.agentRunId(), execution.agentRoundNo()
            ));
        } catch (Exception exception) {
            log.warn("AI 调用日志创建失败：scene={}, model={}",
                    execution.scene(), execution.modelName(), exception);
            return null;
        }
    }

    private AiCallLogCompletionCommand completion(Long callLogId,
                                                   AiCallLogStatusEnum status,
                                                   String responseText,
                                                   String errorMessage,
                                                   long duration,
                                                   ResolvedExecution execution,
                                                   AiChatResult result,
                                                   String traceId,
                                                   AiCallFailureTypeEnum failureType,
                                                   boolean degraded,
                                                   String degradationReason) {
        if (callLogId == null) {
            return null;
        }
        String loggedResponse = execution.contentLoggingPolicy() == AiContentLoggingPolicy.METADATA_ONLY
                ? null : responseText;
        return new AiCallLogCompletionCommand(
                callLogId, status, loggedResponse, errorMessage, duration,
                result == null ? execution.modelName() : normalizedModel(result.requestedModel(), execution.modelName()),
                result == null ? execution.modelName() : normalizedModel(result.actualModel(), execution.modelName()),
                result == null || result.retryCount() == null ? 0 : Math.max(result.retryCount(), 0),
                result == null ? null : result.finishReason(), result == null ? null : result.usage(),
                result == null ? null : result.providerRequestId(), result != null && result.fallbackUsed(),
                result == null ? null : result.fallbackReason(), traceId, failureType,
                degraded, degradationReason, result == null ? List.of() : result.attempts()
        );
    }

    private void completeSafely(AiCallLogCompletionCommand command) {
        if (command == null) {
            return;
        }
        try {
            if (!aiCallLogService.complete(command)) {
                log.warn("AI 调用日志终态未更新，可能已完成：logId={}", command.logId());
            }
        } catch (Exception exception) {
            log.warn("AI 调用日志终态更新失败：logId={}", command.logId(), exception);
        }
    }

    private <T> AiExecutionResult<T> result(T data,
                                            Long callLogId,
                                            ResolvedExecution execution,
                                            AiChatResult result,
                                            long duration,
                                            boolean degraded,
                                            String degradationReason,
                                            String traceId) {
        return new AiExecutionResult<>(
                data, callLogId, normalizedModel(result.requestedModel(), execution.modelName()),
                normalizedModel(result.actualModel(), execution.modelName()),
                result.retryCount() == null ? 0 : Math.max(result.retryCount(), 0), duration,
                result.finishReason(), result.usage(), result.providerRequestId(),
                result.fallbackUsed(), result.fallbackReason(), degraded, degradationReason, traceId,
                execution.promptCode(), execution.promptVersion()
        );
    }

    private AiChatResult failureMetadata(ResolvedExecution execution,
                                         AiInvocationException exception) {
        int retryCount = exception.getRetryCount() == null ? 0 : Math.max(exception.getRetryCount(), 0);
        return new AiChatResult(
                null, List.of(), null, null, null,
                normalizedModel(exception.getRequestedModel(), execution.modelName()),
                normalizedModel(exception.getModelName(), execution.modelName()), retryCount,
                exception.isModelFallbackUsed(), exception.getModelFallbackReason()
        ).withAttempts(exception.getAttempts());
    }

    private AiCallFailureTypeEnum mapFailureType(AiInvocationException exception) {
        AiFailureTypeEnum type = exception.getFailureType();
        return switch (type) {
            case CONFIG_ERROR -> AiCallFailureTypeEnum.CONFIG;
            case TIMEOUT -> AiCallFailureTypeEnum.TIMEOUT;
            case NETWORK_ERROR -> AiCallFailureTypeEnum.NETWORK;
            case RATE_LIMITED -> AiCallFailureTypeEnum.RATE_LIMIT;
            case UPSTREAM_SERVER_ERROR -> AiCallFailureTypeEnum.UPSTREAM_SERVER;
            case UPSTREAM_REJECTED -> exception.getHttpStatusCode() != null
                    && (exception.getHttpStatusCode() == 401 || exception.getHttpStatusCode() == 403)
                    ? AiCallFailureTypeEnum.AUTH
                    : AiCallFailureTypeEnum.UPSTREAM_REJECTED;
            case INVALID_RESPONSE -> AiCallFailureTypeEnum.PROTOCOL;
            case CIRCUIT_OPEN -> AiCallFailureTypeEnum.CIRCUIT_OPEN;
            case CONCURRENCY_LIMIT -> AiCallFailureTypeEnum.CONCURRENCY_LIMIT;
            case CONTENT_BLOCKED -> AiCallFailureTypeEnum.CONTENT_BLOCKED;
            case FEATURE_DISABLED -> AiCallFailureTypeEnum.FEATURE_DISABLED;
            case INTERNAL_ERROR -> AiCallFailureTypeEnum.INTERNAL;
        };
    }

    private AiCallLogStatusEnum statusFor(AiCallFailureTypeEnum type) {
        if (type == AiCallFailureTypeEnum.TIMEOUT) {
            return AiCallLogStatusEnum.TIMEOUT;
        }
        if (type == AiCallFailureTypeEnum.PROTOCOL
                || type == AiCallFailureTypeEnum.RESPONSE_PARSE
                || type == AiCallFailureTypeEnum.BUSINESS_VALIDATION) {
            return AiCallLogStatusEnum.PARSE_FAILED;
        }
        return AiCallLogStatusEnum.FAILED;
    }

    private String resolveTraceId(String traceId) {
        return TraceContext.explicitOrCurrent(traceId);
    }

    private String normalizedModel(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String buildRequestText(String systemPrompt, String userPrompt) {
        return JSONUtil.createObj().set("systemPrompt", systemPrompt).set("userPrompt", userPrompt).toString();
    }

    private String requestLogText(ResolvedExecution execution) {
        if (execution.contentLoggingPolicy() == AiContentLoggingPolicy.METADATA_ONLY) {
            return execution.requestLogSummary();
        }
        return buildRequestText(execution.systemPrompt(), execution.userPrompt());
    }

    private long elapsedSince(long startTime) {
        return Math.max(System.currentTimeMillis() - startTime, 0L);
    }

    private record ResolvedExecution(
            Long userId, String modelName, String scene, String promptCode,
            Long promptTemplateId, Integer promptVersion, String promptSource,
            String systemPrompt, String userPrompt, String parseFailureMessage, String traceId,
            AiContentLoggingPolicy contentLoggingPolicy, String requestLogSummary,
            String agentRunId, Integer agentRoundNo
    ) {
    }
}
