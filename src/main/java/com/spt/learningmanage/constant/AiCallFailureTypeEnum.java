package com.spt.learningmanage.constant;

/**
 * AI 调用管线统一失败分类。该分类用于审计日志，不替代模型客户端协议层的
 * {@link AiFailureTypeEnum}。
 */
public enum AiCallFailureTypeEnum {
    CONFIG,
    AUTH,
    RATE_LIMIT,
    NETWORK,
    TIMEOUT,
    UPSTREAM_REJECTED,
    UPSTREAM_SERVER,
    PROTOCOL,
    RESPONSE_PARSE,
    BUSINESS_VALIDATION,
    INTERNAL
}
