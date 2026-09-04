package com.spt.learningmanage.exception;

import com.spt.learningmanage.constant.AiFailureTypeEnum;
import lombok.Getter;

/**
 * AI 模型调用异常。
 *
 * safeMessage 可写入调用日志并返回用户；父类 message 仅用于服务端排查。
 */
@Getter
public class AiInvocationException extends RuntimeException {

    private final AiFailureTypeEnum failureType;

    private final String requestedModel;

    private final String modelName;

    private final Integer retryCount;

    private final String safeMessage;

    private final Integer httpStatusCode;

    private final boolean modelFallbackUsed;

    private final AiFailureTypeEnum modelFallbackReason;

    public AiInvocationException(AiFailureTypeEnum failureType,
                                 String modelName,
                                 Integer retryCount,
                                 String safeMessage,
                                 String internalMessage,
                                 Throwable cause) {
        this(failureType, modelName, modelName, retryCount, safeMessage, internalMessage,
                cause, null, false, null);
    }

    public AiInvocationException(AiFailureTypeEnum failureType,
                                 String modelName,
                                 Integer retryCount,
                                 String safeMessage,
                                 String internalMessage,
                                 Throwable cause,
                                 Integer httpStatusCode) {
        this(failureType, modelName, modelName, retryCount, safeMessage, internalMessage,
                cause, httpStatusCode, false, null);
    }

    public AiInvocationException(AiFailureTypeEnum failureType,
                                 String requestedModel,
                                 String modelName,
                                 Integer retryCount,
                                 String safeMessage,
                                 String internalMessage,
                                 Throwable cause,
                                 Integer httpStatusCode,
                                 boolean modelFallbackUsed,
                                 AiFailureTypeEnum modelFallbackReason) {
        super(internalMessage, cause);
        this.failureType = failureType;
        this.requestedModel = requestedModel;
        this.modelName = modelName;
        this.retryCount = retryCount;
        this.safeMessage = safeMessage;
        this.httpStatusCode = httpStatusCode;
        this.modelFallbackUsed = modelFallbackUsed;
        this.modelFallbackReason = modelFallbackReason;
    }

    public boolean isRetryable() {
        return failureType != null && failureType.isRetryable();
    }
}
