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

    private final String modelName;

    private final Integer retryCount;

    private final String safeMessage;

    public AiInvocationException(AiFailureTypeEnum failureType,
                                 String modelName,
                                 Integer retryCount,
                                 String safeMessage,
                                 String internalMessage,
                                 Throwable cause) {
        super(internalMessage, cause);
        this.failureType = failureType;
        this.modelName = modelName;
        this.retryCount = retryCount;
        this.safeMessage = safeMessage;
    }

    public boolean isRetryable() {
        return failureType != null && failureType.isRetryable();
    }
}
