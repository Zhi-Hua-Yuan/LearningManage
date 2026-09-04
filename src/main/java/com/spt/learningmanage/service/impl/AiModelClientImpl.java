package com.spt.learningmanage.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.ai.governance.AiContentSanitizer;
import com.spt.learningmanage.ai.governance.AiFeatureGate;
import com.spt.learningmanage.ai.governance.AiResilientCallExecutor;
import com.spt.learningmanage.ai.governance.AiSanitizedContent;
import com.spt.learningmanage.ai.governance.AiSanitizationStatus;
import com.spt.learningmanage.client.ai.AiChatCommandValidator;
import com.spt.learningmanage.client.ai.AiChatRequestMapper;
import com.spt.learningmanage.client.ai.AiChatResponseParser;
import com.spt.learningmanage.client.ai.AiHttpTransport;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.exception.AiInvocationException;
import com.spt.learningmanage.model.dto.ai.AiHttpResponse;
import com.spt.learningmanage.model.dto.ai.AiInvocationResult;
import com.spt.learningmanage.model.dto.ai.chat.AiChatCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiAttemptSummary;
import com.spt.learningmanage.model.dto.ai.chat.AiChatMessage;
import com.spt.learningmanage.model.dto.ai.chat.AiChatResult;
import com.spt.learningmanage.model.dto.ai.chat.AiFunctionCall;
import com.spt.learningmanage.model.dto.ai.chat.AiToolCall;
import com.spt.learningmanage.model.dto.ai.chat.AiToolChoice;
import com.spt.learningmanage.service.AiModelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AiModelClientImpl implements AiModelClient {

    private static final Logger log = LoggerFactory.getLogger(AiModelClientImpl.class);

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 60000;
    private static final int CONNECT_TIMEOUT_MIN_MS = 1000;
    private static final int CONNECT_TIMEOUT_MAX_MS = 30000;
    private static final int READ_TIMEOUT_MIN_MS = 5000;
    private static final int READ_TIMEOUT_MAX_MS = 300000;
    private final AiProperties aiProperties;

    private final AiHttpTransport aiHttpTransport;

    private final AiChatCommandValidator commandValidator;

    private final AiChatRequestMapper requestMapper;

    private final AiChatResponseParser responseParser;

    private final AiFeatureGate featureGate;

    private final AiContentSanitizer contentSanitizer;

    private final AiResilientCallExecutor resilientCallExecutor;

    @Autowired
    public AiModelClientImpl(AiProperties aiProperties,
                             AiHttpTransport aiHttpTransport,
                             AiChatCommandValidator commandValidator,
                             AiChatRequestMapper requestMapper,
                             AiChatResponseParser responseParser,
                             AiFeatureGate featureGate,
                             AiContentSanitizer contentSanitizer,
                             AiResilientCallExecutor resilientCallExecutor) {
        this.aiProperties = aiProperties;
        this.aiHttpTransport = aiHttpTransport;
        this.commandValidator = commandValidator;
        this.requestMapper = requestMapper;
        this.responseParser = responseParser;
        this.featureGate = featureGate;
        this.contentSanitizer = contentSanitizer;
        this.resilientCallExecutor = resilientCallExecutor;
    }

    public AiModelClientImpl(AiProperties aiProperties,
                             AiHttpTransport aiHttpTransport,
                             AiChatCommandValidator commandValidator,
                             AiChatRequestMapper requestMapper,
                             AiChatResponseParser responseParser) {
        this(aiProperties, aiHttpTransport, commandValidator, requestMapper, responseParser,
                new AiFeatureGate(aiProperties),
                new com.spt.learningmanage.ai.governance.DefaultAiContentSanitizer(
                        new ObjectMapper(), aiProperties),
                new AiResilientCallExecutor(aiProperties));
    }

    public AiModelClientImpl(AiProperties aiProperties, AiHttpTransport aiHttpTransport) {
        this(aiProperties, aiHttpTransport,
                new AiChatCommandValidator(new ObjectMapper()),
                new AiChatRequestMapper(new ObjectMapper()),
                new AiChatResponseParser(new ObjectMapper()),
                new AiFeatureGate(aiProperties),
                new com.spt.learningmanage.ai.governance.DefaultAiContentSanitizer(
                        new ObjectMapper(), aiProperties),
                new AiResilientCallExecutor(aiProperties));
    }

    @Override
    public AiInvocationResult invoke(String primaryModel, String systemPrompt, String userPrompt) {
        validateConfiguration(safeTrim(primaryModel));
        AiChatCommand command = new AiChatCommand(
                primaryModel,
                List.of(AiChatMessage.system(systemPrompt), AiChatMessage.user(userPrompt)),
                List.of(),
                null,
                null,
                null
        );
        AiChatResult chatResult = chat(command);
        if (StrUtil.isBlank(chatResult.content())) {
            throw invalidResponse(chatResult.actualModel(), chatResult.retryCount(),
                    "旧 invoke 接口收到非文本响应");
        }
        return new AiInvocationResult(
                chatResult.content(),
                chatResult.actualModel(),
                chatResult.retryCount()
        );
    }

    @Override
    public AiChatResult chat(AiChatCommand command) {
        commandValidator.validate(command);
        String normalizedPrimaryModel = safeTrim(command.requestedModel());
        featureGate.requireChatEnabled(normalizedPrimaryModel);
        validateConfiguration(normalizedPrimaryModel);
        AiChatCommand normalizedCommand = sanitizeCommand(command.withRequestedModel(normalizedPrimaryModel));
        long deadlineNanos = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(aiProperties.getResilience().getTotalTimeoutMs());

        try {
            return chatOnce(normalizedCommand, normalizedPrimaryModel, 0, null, deadlineNanos);
        } catch (AiInvocationException primaryException) {
            String fallbackModel = safeTrim(aiProperties.getFallbackModel());
            if (!primaryException.isRetryable()
                    || StrUtil.isBlank(fallbackModel)
                    || StrUtil.equals(normalizedPrimaryModel, fallbackModel)) {
                throw primaryException;
            }

            log.warn("AI 主模型调用失败，使用兜底模型重试: primaryModel={}, fallbackModel={}, failureType={}",
                    normalizedPrimaryModel, fallbackModel, primaryException.getFailureType());
            try {
                AiChatCommand fallbackCommand = normalizedCommand.withRequestedModel(fallbackModel);
                AiChatResult fallbackResult = chatOnce(fallbackCommand, normalizedPrimaryModel, 1,
                        primaryException.getFailureType(), deadlineNanos);
                List<AiAttemptSummary> attempts = new ArrayList<>(primaryException.getAttempts());
                attempts.addAll(fallbackResult.attempts());
                return fallbackResult.withAttempts(attempts);
            } catch (AiInvocationException fallbackException) {
                List<AiAttemptSummary> attempts = new ArrayList<>(primaryException.getAttempts());
                attempts.addAll(fallbackException.getAttempts());
                AiInvocationException terminalException = new AiInvocationException(
                        fallbackException.getFailureType(), normalizedPrimaryModel,
                        fallbackException.getModelName(), fallbackException.getRetryCount(),
                        fallbackException.getSafeMessage(), fallbackException.getMessage(),
                        fallbackException, fallbackException.getHttpStatusCode(), true,
                        primaryException.getFailureType(), attempts
                );
                terminalException.addSuppressed(primaryException);
                throw terminalException;
            }
        }
    }

    private AiChatResult chatOnce(AiChatCommand command,
                                  String requestedModel,
                                  int retryCount,
                                  AiFailureTypeEnum fallbackReason,
                                  long deadlineNanos) {
        ensureTimeRemaining(command.requestedModel(), retryCount, deadlineNanos);
        long attemptStartedAt = System.nanoTime();
        try {
            AiChatResult result = resilientCallExecutor.execute(requestedModel, command.requestedModel(), retryCount,
                    () -> executeOnce(command, requestedModel, retryCount, fallbackReason, deadlineNanos));
            AiAttemptSummary attempt = new AiAttemptSummary(command.requestedModel(), result.usage(),
                    result.providerRequestId(), null, elapsedMillis(attemptStartedAt));
            return result.withAttempts(List.of(attempt));
        } catch (AiInvocationException exception) {
            if (!exception.getAttempts().isEmpty()) {
                throw exception;
            }
            throw exception.withAttempts(List.of(new AiAttemptSummary(
                    command.requestedModel(), null, null, exception.getFailureType(), elapsedMillis(attemptStartedAt)
            )));
        }
    }

    private AiChatResult executeOnce(AiChatCommand command,
                                     String requestedModel,
                                     int retryCount,
                                     AiFailureTypeEnum fallbackReason,
                                     long deadlineNanos) {
        String model = command.requestedModel();
        String requestBody;
        try {
            requestBody = requestMapper.toJson(command);
        } catch (RuntimeException e) {
            throw invocationException(
                    AiFailureTypeEnum.INTERNAL_ERROR,
                    model,
                    retryCount,
                    "AI 请求构造失败，请联系管理员",
                    "构造 AI 上游请求失败: model=" + model,
                    e
            );
        }
        AiHttpResponse response;
        try {
            response = aiHttpTransport.postChat(
                    StrUtil.removeSuffix(aiProperties.getBaseUrl().trim(), "/") + "/chat/completions",
                    aiProperties.getApiKey().trim(),
                    requestBody,
                    resolveConnectTimeoutMs(),
                    resolveReadTimeoutMs(deadlineNanos)
            );
        } catch (Exception e) {
            if (containsSocketTimeout(e)) {
                throw invocationException(
                        AiFailureTypeEnum.TIMEOUT,
                        model,
                        retryCount,
                        "AI 服务响应超时，请稍后重试",
                        "AI 请求超时: model=" + model,
                        e
                );
            }
            throw invocationException(
                    AiFailureTypeEnum.NETWORK_ERROR,
                    model,
                    retryCount,
                    "AI 服务暂时不可用，请稍后重试",
                    "AI 网络请求失败: model=" + model + ", cause=" + e.getClass().getSimpleName(),
                    e
            );
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            AiFailureTypeEnum failureType = resolveHttpFailureType(response.statusCode());
            throw invocationException(
                    failureType,
                    model,
                    retryCount,
                    safeMessageFor(failureType),
                    "AI 上游响应异常: model=" + model
                            + ", status=" + response.statusCode(),
                    null,
                    response.statusCode()
            );
        }

        try {
            AiChatResult result = responseParser.parse(
                    response,
                    requestedModel,
                    model,
                    retryCount,
                    fallbackReason
            );
            validateResponseAgainstCommand(command, result);
            return result;
        } catch (Exception e) {
            throw invocationException(
                    AiFailureTypeEnum.INVALID_RESPONSE,
                    model,
                    retryCount,
                    "AI 返回结果格式异常，请重试",
                    "解析 AI 上游响应失败: model=" + model,
                    e
            );
        }
    }

    private void validateResponseAgainstCommand(AiChatCommand command, AiChatResult result) {
        if (result.toolCalls().isEmpty()) {
            return;
        }
        if (command.tools().isEmpty()) {
            throw new IllegalArgumentException("模型返回了请求中未声明的 Tool Call");
        }
        if (command.toolChoice() != null && command.toolChoice().mode() == AiToolChoice.Mode.NONE) {
            throw new IllegalArgumentException("toolChoice=NONE 时模型不得返回 Tool Call");
        }
        Set<String> registeredFunctions = command.tools().stream()
                .map(tool -> tool.function().name())
                .collect(Collectors.toSet());
        for (var toolCall : result.toolCalls()) {
            if (!registeredFunctions.contains(toolCall.function().name())) {
                throw new IllegalArgumentException("模型返回了未声明函数: " + toolCall.function().name());
            }
            if (command.toolChoice() != null
                    && command.toolChoice().mode() == AiToolChoice.Mode.FUNCTION
                    && !command.toolChoice().functionName().equals(toolCall.function().name())) {
                throw new IllegalArgumentException("模型返回函数与强制 toolChoice 不一致");
            }
        }
    }

    private void validateConfiguration(String primaryModel) {
        if (StrUtil.hasBlank(aiProperties.getBaseUrl(), aiProperties.getApiKey(), primaryModel)) {
            throw invocationException(
                    AiFailureTypeEnum.CONFIG_ERROR,
                    primaryModel,
                    0,
                    "AI 服务配置异常，请联系管理员",
                    "AI 配置不完整，请检查 ai.base-url、ai.api-key、ai.model",
                    null
            );
        }
    }

    private AiFailureTypeEnum resolveHttpFailureType(int statusCode) {
        if (statusCode == 408 || statusCode == 504) {
            return AiFailureTypeEnum.TIMEOUT;
        }
        if (statusCode == 429) {
            return AiFailureTypeEnum.RATE_LIMITED;
        }
        if (statusCode >= 500) {
            return AiFailureTypeEnum.UPSTREAM_SERVER_ERROR;
        }
        return AiFailureTypeEnum.UPSTREAM_REJECTED;
    }

    private String safeMessageFor(AiFailureTypeEnum failureType) {
        return switch (failureType) {
            case TIMEOUT -> "AI 服务响应超时，请稍后重试";
            case RATE_LIMITED -> "AI 服务当前请求较多，请稍后重试";
            case UPSTREAM_REJECTED -> "AI 服务请求被拒绝，请联系管理员";
            default -> "AI 服务暂时不可用，请稍后重试";
        };
    }

    private AiInvocationException invalidResponse(String model, int retryCount, String internalMessage) {
        return invocationException(
                AiFailureTypeEnum.INVALID_RESPONSE,
                model,
                retryCount,
                "AI 返回结果格式异常，请重试",
                internalMessage + ": model=" + model,
                null
        );
    }

    private AiInvocationException invocationException(AiFailureTypeEnum failureType,
                                                      String model,
                                                      int retryCount,
                                                      String safeMessage,
                                                      String internalMessage,
                                                      Throwable cause) {
        return invocationException(failureType, model, retryCount, safeMessage, internalMessage, cause, null);
    }

    private AiInvocationException invocationException(AiFailureTypeEnum failureType,
                                                      String model,
                                                      int retryCount,
                                                      String safeMessage,
                                                      String internalMessage,
                                                      Throwable cause,
                                                      Integer httpStatusCode) {
        return new AiInvocationException(
                failureType,
                model,
                retryCount,
                safeMessage,
                internalMessage,
                cause,
                httpStatusCode
        );
    }

    private int resolveConnectTimeoutMs() {
        return normalizeTimeout(
                aiProperties.getConnectTimeoutMs(),
                DEFAULT_CONNECT_TIMEOUT_MS,
                CONNECT_TIMEOUT_MIN_MS,
                CONNECT_TIMEOUT_MAX_MS,
                "connectTimeoutMs"
        );
    }

    private int resolveReadTimeoutMs() {
        return normalizeTimeout(
                aiProperties.getReadTimeoutMs(),
                DEFAULT_READ_TIMEOUT_MS,
                READ_TIMEOUT_MIN_MS,
                READ_TIMEOUT_MAX_MS,
                "readTimeoutMs"
        );
    }

    private int resolveReadTimeoutMs(long deadlineNanos) {
        long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
        if (remainingMillis <= 0) {
            return 1;
        }
        return (int) Math.max(1L, Math.min(resolveReadTimeoutMs(), remainingMillis));
    }

    private void ensureTimeRemaining(String model, int retryCount, long deadlineNanos) {
        if (deadlineNanos - System.nanoTime() <= 0) {
            throw invocationException(
                    AiFailureTypeEnum.TIMEOUT,
                    model,
                    retryCount,
                    "AI 服务响应超时，请稍后重试",
                    "AI logical call deadline exceeded before model attempt: model=" + model,
                    null
            );
        }
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos), 0L);
    }

    private AiChatCommand sanitizeCommand(AiChatCommand command) {
        List<AiChatMessage> messages = command.messages().stream()
                .map(this::sanitizeMessage)
                .toList();
        return new AiChatCommand(command.requestedModel(), messages, command.tools(), command.toolChoice(),
                command.temperature(), command.maxOutputTokens());
    }

    private AiChatMessage sanitizeMessage(AiChatMessage message) {
        String content = sanitizeProviderContent(message.content());
        List<AiToolCall> toolCalls = message.toolCalls().stream()
                .map(this::sanitizeToolCall)
                .toList();
        return new AiChatMessage(message.role(), content, message.toolCallId(), toolCalls);
    }

    private AiToolCall sanitizeToolCall(AiToolCall toolCall) {
        if (toolCall.function() == null) {
            return toolCall;
        }
        return new AiToolCall(toolCall.id(), toolCall.type(), new AiFunctionCall(
                toolCall.function().name(), sanitizeProviderContent(toolCall.function().arguments())
        ));
    }

    private String sanitizeProviderContent(String content) {
        AiSanitizedContent sanitized = contentSanitizer.sanitizeForProvider(content);
        if (sanitized.status() == AiSanitizationStatus.BLOCKED) {
            throw invocationException(
                    AiFailureTypeEnum.CONTENT_BLOCKED,
                    null,
                    0,
                    "请求包含禁止发送的敏感信息",
                    "AI provider content sanitization was blocked",
                    null
            );
        }
        return sanitized.value();
    }

    private int normalizeTimeout(Integer configured,
                                 int defaultValue,
                                 int minValue,
                                 int maxValue,
                                 String propertyName) {
        if (configured == null || configured < minValue || configured > maxValue) {
            log.warn("AI 超时配置不合法，使用默认值: property={}, configured={}, default={}",
                    propertyName, configured, defaultValue);
            return defaultValue;
        }
        return configured;
    }

    private boolean containsSocketTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            if (current instanceof ConnectException
                    && StrUtil.containsIgnoreCase(current.getMessage(), "timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String safeTrim(String value) {
        return value == null ? null : value.trim();
    }

}
