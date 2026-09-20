package com.spt.learningmanage.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.ai.governance.AiContentSanitizer;
import com.spt.learningmanage.ai.governance.AiFeatureGate;
import com.spt.learningmanage.ai.governance.AiResilientCallExecutor;
import com.spt.learningmanage.ai.governance.AiSanitizedContent;
import com.spt.learningmanage.ai.governance.AiSanitizationStatus;
import com.spt.learningmanage.ai.governance.DefaultAiContentSanitizer;
import com.spt.learningmanage.client.ai.AiChatCommandValidator;
import com.spt.learningmanage.client.ai.spi.AiChatAdapter;
import com.spt.learningmanage.client.ai.spi.AiChatDispatchContext;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.exception.AiInvocationException;
import com.spt.learningmanage.model.dto.ai.chat.AiAttemptSummary;
import com.spt.learningmanage.model.dto.ai.chat.AiChatCommand;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 业务模型调用的治理入口（对应上游方案的 {@code GovernedAiModelClient} 角色）。
 *
 * <p>职责：Feature Gate、脱敏、总时限、主备模型、Usage/Cost 汇总、
 * Bulkhead 与熔断（经 {@link AiResilientCallExecutor}）、错误映射，
 * 以及响应与命令一致性校验。协议细节（URL、JSON、header、状态码分类）
 * 全部下沉到 {@link AiChatAdapter} 实现，因此本类不感知 Spring AI 的存在。</p>
 */
@Service
public class AiModelClientImpl implements AiModelClient {

    private static final Logger log = LoggerFactory.getLogger(AiModelClientImpl.class);

    private final AiProperties aiProperties;

    private final AiChatAdapter aiChatAdapter;

    private final AiChatCommandValidator commandValidator;

    private final AiFeatureGate featureGate;

    private final AiContentSanitizer contentSanitizer;

    private final AiResilientCallExecutor resilientCallExecutor;

    @Autowired
    public AiModelClientImpl(AiProperties aiProperties,
                             AiChatAdapter aiChatAdapter,
                             AiChatCommandValidator commandValidator,
                             AiFeatureGate featureGate,
                             AiContentSanitizer contentSanitizer,
                             AiResilientCallExecutor resilientCallExecutor) {
        this.aiProperties = aiProperties;
        this.aiChatAdapter = aiChatAdapter;
        this.commandValidator = commandValidator;
        this.featureGate = featureGate;
        this.contentSanitizer = contentSanitizer;
        this.resilientCallExecutor = resilientCallExecutor;
    }

    public AiModelClientImpl(AiProperties aiProperties, AiChatAdapter aiChatAdapter) {
        this(aiProperties, aiChatAdapter,
                new AiChatCommandValidator(new ObjectMapper()),
                new AiFeatureGate(aiProperties),
                new DefaultAiContentSanitizer(new ObjectMapper(), aiProperties),
                new AiResilientCallExecutor(aiProperties));
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
        AiChatResult result = aiChatAdapter.chat(command,
                new AiChatDispatchContext(requestedModel, retryCount, fallbackReason, deadlineNanos));
        try {
            validateResponseAgainstCommand(command, result);
        } catch (RuntimeException e) {
            throw invocationException(
                    AiFailureTypeEnum.INVALID_RESPONSE,
                    model,
                    retryCount,
                    "AI 返回结果格式异常，请重试",
                    "解析 AI 上游响应失败: model=" + model,
                    e
            );
        }
        return result;
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

    private AiInvocationException invocationException(AiFailureTypeEnum failureType,
                                                      String model,
                                                      int retryCount,
                                                      String safeMessage,
                                                      String internalMessage,
                                                      Throwable cause) {
        return new AiInvocationException(
                failureType,
                model,
                retryCount,
                safeMessage,
                internalMessage,
                cause,
                null
        );
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

    private String safeTrim(String value) {
        return value == null ? null : value.trim();
    }

}
