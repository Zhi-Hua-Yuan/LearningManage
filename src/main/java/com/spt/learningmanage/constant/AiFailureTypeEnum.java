package com.spt.learningmanage.constant;

import lombok.Getter;

/**
 * AI 单次调用失败类型及其是否允许使用兜底模型重试。
 */
@Getter
public enum AiFailureTypeEnum {

    CONFIG_ERROR(false),
    TIMEOUT(true),
    NETWORK_ERROR(true),
    RATE_LIMITED(true),
    UPSTREAM_SERVER_ERROR(true),
    UPSTREAM_REJECTED(false),
    INVALID_RESPONSE(true),
    INTERNAL_ERROR(false);

    private final boolean retryable;

    AiFailureTypeEnum(boolean retryable) {
        this.retryable = retryable;
    }
}
