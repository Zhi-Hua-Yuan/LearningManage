package com.spt.learningmanage.client.ai.adapter;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.client.ai.AiChatRequestMapper;
import com.spt.learningmanage.client.ai.AiChatResponseParser;
import com.spt.learningmanage.client.ai.AiHttpTransport;
import com.spt.learningmanage.client.ai.AiTimeoutPolicy;
import com.spt.learningmanage.client.ai.spi.AiChatAdapter;
import com.spt.learningmanage.client.ai.spi.AiChatDispatchContext;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.exception.AiInvocationException;
import com.spt.learningmanage.model.dto.ai.AiHttpResponse;
import com.spt.learningmanage.model.dto.ai.chat.AiChatCommand;
import com.spt.learningmanage.model.dto.ai.chat.AiChatResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

/**
 * 既有传输协议的适配器：Hutool HTTP 直连 Qwen 兼容接口。
 *
 * <p>代码从治理层的 executeOnce 原样抽出（request 映射 → 传输 →
 * 状态码分类 → 响应解析），行为逐位不变。抽出的目的只是把「传输」
 * 从「治理」里分离，使 Spring AI 适配器可以与它并存并可回滚。</p>
 *
 * <p>与 Spring AI 适配器互斥装配：两者都实现同一个 SPI，而治理层只注入
 * 单个适配器，若同时进容器会变成两个候选。默认（不配置
 * {@code ai.chat.adapter}）装配本实现。</p>
 */
@Component
@ConditionalOnProperty(name = "ai.chat.adapter", havingValue = "legacy", matchIfMissing = true)
public class LegacyAiChatAdapter implements AiChatAdapter {

    private final AiProperties aiProperties;

    private final AiHttpTransport aiHttpTransport;

    private final AiChatRequestMapper requestMapper;

    private final AiChatResponseParser responseParser;

    @Autowired
    public LegacyAiChatAdapter(AiProperties aiProperties,
                               AiHttpTransport aiHttpTransport,
                               AiChatRequestMapper requestMapper,
                               AiChatResponseParser responseParser) {
        this.aiProperties = aiProperties;
        this.aiHttpTransport = aiHttpTransport;
        this.requestMapper = requestMapper;
        this.responseParser = responseParser;
    }

    public LegacyAiChatAdapter(AiProperties aiProperties, AiHttpTransport aiHttpTransport) {
        this(aiProperties, aiHttpTransport,
                new AiChatRequestMapper(new ObjectMapper()),
                new AiChatResponseParser(new ObjectMapper()));
    }

    @Override
    public AiChatResult chat(AiChatCommand command, AiChatDispatchContext context) {
        String model = command.requestedModel();
        int retryCount = context.retryCount();
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
                    resolveReadTimeoutMs(context.deadlineNanos())
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
            return responseParser.parse(
                    response,
                    context.requestedModel(),
                    model,
                    retryCount,
                    context.fallbackReason()
            );
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
        return AiTimeoutPolicy.connectTimeoutMs(aiProperties);
    }

    private int resolveReadTimeoutMs(long deadlineNanos) {
        long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
        if (remainingMillis <= 0) {
            return 1;
        }
        return (int) Math.max(1L, Math.min(AiTimeoutPolicy.readTimeoutMs(aiProperties), remainingMillis));
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
}
