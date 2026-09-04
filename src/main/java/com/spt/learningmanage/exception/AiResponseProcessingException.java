package com.spt.learningmanage.exception;

import com.spt.learningmanage.constant.AiCallFailureTypeEnum;

/**
 * 模型调用已经返回内容，但场景解析或业务校验失败。
 */
public class AiResponseProcessingException extends RuntimeException {

    private final String safeMessage;
    private final AiCallFailureTypeEnum failureType;

    public AiResponseProcessingException(String safeMessage, Throwable cause) {
        this(AiCallFailureTypeEnum.RESPONSE_PARSE, safeMessage, cause);
    }

    public AiResponseProcessingException(AiCallFailureTypeEnum failureType,
                                         String safeMessage,
                                         Throwable cause) {
        super(buildInternalMessage(safeMessage), cause);
        if (failureType != AiCallFailureTypeEnum.RESPONSE_PARSE
                && failureType != AiCallFailureTypeEnum.BUSINESS_VALIDATION) {
            throw new IllegalArgumentException("响应处理失败类型不合法");
        }
        this.safeMessage = safeMessage;
        this.failureType = failureType;
    }

    public String getSafeMessage() {
        return safeMessage;
    }

    public AiCallFailureTypeEnum getFailureType() {
        return failureType;
    }

    public static AiResponseProcessingException parse(String safeMessage, Throwable cause) {
        return new AiResponseProcessingException(AiCallFailureTypeEnum.RESPONSE_PARSE, safeMessage, cause);
    }

    public static AiResponseProcessingException businessValidation(String safeMessage, Throwable cause) {
        return new AiResponseProcessingException(AiCallFailureTypeEnum.BUSINESS_VALIDATION, safeMessage, cause);
    }

    private static String buildInternalMessage(String safeMessage) {
        if (safeMessage == null || safeMessage.isBlank()) {
            throw new IllegalArgumentException("安全错误信息不能为空");
        }
        return "AI 响应处理失败：" + safeMessage;
    }
}
