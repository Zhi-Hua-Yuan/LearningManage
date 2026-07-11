package com.spt.learningmanage.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.spt.learningmanage.client.ai.AiHttpTransport;
import com.spt.learningmanage.config.AiProperties;
import com.spt.learningmanage.constant.AiFailureTypeEnum;
import com.spt.learningmanage.exception.AiInvocationException;
import com.spt.learningmanage.model.dto.ai.AiHttpResponse;
import com.spt.learningmanage.model.dto.ai.AiInvocationResult;
import com.spt.learningmanage.service.AiModelClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

@Service
@RequiredArgsConstructor
public class AiModelClientImpl implements AiModelClient {

    private static final Logger log = LoggerFactory.getLogger(AiModelClientImpl.class);

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 60000;
    private static final int CONNECT_TIMEOUT_MIN_MS = 1000;
    private static final int CONNECT_TIMEOUT_MAX_MS = 30000;
    private static final int READ_TIMEOUT_MIN_MS = 5000;
    private static final int READ_TIMEOUT_MAX_MS = 300000;
    private static final int ERROR_BODY_PREVIEW_MAX_LENGTH = 500;

    private final AiProperties aiProperties;

    private final AiHttpTransport aiHttpTransport;

    @Override
    public AiInvocationResult invoke(String primaryModel, String systemPrompt, String userPrompt) {
        String normalizedPrimaryModel = safeTrim(primaryModel);
        validateConfiguration(normalizedPrimaryModel);

        try {
            return invokeOnce(normalizedPrimaryModel, systemPrompt, userPrompt, 0);
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
                return invokeOnce(fallbackModel, systemPrompt, userPrompt, 1);
            } catch (AiInvocationException fallbackException) {
                fallbackException.addSuppressed(primaryException);
                throw fallbackException;
            }
        }
    }

    private AiInvocationResult invokeOnce(String model,
                                          String systemPrompt,
                                          String userPrompt,
                                          int retryCount) {
        JSONObject requestBody = JSONUtil.createObj()
                .set("model", model)
                .set("messages", JSONUtil.createArray()
                        .put(JSONUtil.createObj().set("role", "system").set("content", systemPrompt))
                        .put(JSONUtil.createObj().set("role", "user").set("content", userPrompt)));

        AiHttpResponse response;
        try {
            response = aiHttpTransport.postChat(
                    StrUtil.removeSuffix(aiProperties.getBaseUrl().trim(), "/") + "/chat/completions",
                    aiProperties.getApiKey().trim(),
                    requestBody.toString(),
                    resolveConnectTimeoutMs(),
                    resolveReadTimeoutMs()
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
                            + ", status=" + response.statusCode()
                            + ", body=" + truncate(response.responseBody(), ERROR_BODY_PREVIEW_MAX_LENGTH),
                    null
            );
        }

        try {
            JSONObject responseJson = JSONUtil.parseObj(response.responseBody());
            JSONArray choices = responseJson.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw invalidResponse(model, retryCount, "AI 响应缺少 choices");
            }
            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject message = firstChoice == null ? null : firstChoice.getJSONObject("message");
            String content = message == null ? null : message.getStr("content");
            if (StrUtil.isBlank(content)) {
                throw invalidResponse(model, retryCount, "AI 响应 content 为空");
            }
            return new AiInvocationResult(content, model, retryCount);
        } catch (AiInvocationException e) {
            throw e;
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
        return new AiInvocationException(
                failureType,
                model,
                retryCount,
                safeMessage,
                internalMessage,
                cause
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

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
