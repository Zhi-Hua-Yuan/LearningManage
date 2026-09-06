package com.spt.learningmanage.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    /**
     * 基础通用错误码 (400xx: 客户端错误, 401xx: 鉴权错误, 500xx: 服务端错误)
     */
    SUCCESS(0, "ok"),
    PARAMS_ERROR(40000, "请求参数错误"),
    NOT_LOGIN_ERROR(40100, "未登录"),
    NO_AUTH_ERROR(40101, "无权限"),
    NOT_FOUND_ERROR(40400, "请求数据不存在"),
    FORBIDDEN_ERROR(40300, "禁止访问"),
    RATE_LIMIT_ERROR(42900, "请求过于频繁"),
    SYSTEM_ERROR(50000, "系统内部异常"),
    OPERATION_ERROR(50001, "操作失败"),

    /**
     * 项目相关 (1xxxx)
     */
    PROJECT_NAME_EMPTY(10001, "项目名称不能为空"),
    PROJECT_ALREADY_EXISTS(10002, "项目已存在"),
    PROJECT_NOT_FOUND(10003, "项目不存在"),

    /**
     * 用户相关 (2xxxx)
     */
    USER_NOT_FOUND(20001, "用户不存在"),
    ACCOUNT_ALREADY_EXISTS(20002, "账号已存在"),
    PASSWORD_ERROR(20003, "密码错误"),

    /**
     * AI 调用相关 (3xxxx)
     */
    AI_SERVICE_UNAVAILABLE(30001, "AI 服务暂时不可用"),
    AI_REQUEST_TIMEOUT(30002, "AI 服务响应超时"),
    AI_RESPONSE_INVALID(30003, "AI 返回结果格式异常"),
    AI_CONFIG_ERROR(30004, "AI 服务配置异常"),
    AI_DRAFT_NOT_CONFIRMABLE(30005, "AI 草稿当前不可确认"),
    AI_DRAFT_EXPIRED(30006, "AI 草稿已过期"),
    AI_DRAFT_SCHEMA_UNSUPPORTED(30007, "AI 草稿版本不受支持"),
    AI_DRAFT_CONFLICT(30008, "AI 草稿状态冲突"),
    AI_DISABLED(30009, "AI 生成功能已关闭"),
    AI_CONCURRENCY_LIMIT(30010, "AI 服务当前请求较多"),
    AI_CONTENT_BLOCKED(30011, "请求包含禁止发送的敏感信息"),

    /** Knowledge index operations (31xxx). */
    KNOWLEDGE_INDEX_DISABLED(31001, "知识索引服务未启用"),
    KNOWLEDGE_EVENT_NOT_FOUND(31002, "知识索引事件不存在"),
    KNOWLEDGE_EVENT_NOT_REPLAYABLE(31003, "知识索引事件不可重放"),
    KNOWLEDGE_BACKFILL_NOT_FOUND(31004, "知识索引回填任务不存在"),
    KNOWLEDGE_BACKFILL_CONFLICT(31005, "知识索引回填任务冲突"),
    EMBEDDING_UNAVAILABLE(31006, "Embedding 服务暂时不可用"),
    EMBEDDING_DIMENSION_MISMATCH(31007, "Embedding 向量维度不匹配"),
    VECTOR_STORE_UNAVAILABLE(31008, "向量库暂时不可用"),
    VECTOR_COLLECTION_INVALID(31009, "向量集合配置不合法"),

    /** Permission-aware RAG (32xxx). */
    RAG_DISABLED(32001, "RAG 问答功能未启用"),
    KNOWLEDGE_INDEX_NOT_READY(32002, "知识索引尚未就绪"),
    RAG_DEPENDENCY_UNAVAILABLE(32003, "RAG 依赖服务暂时不可用"),
    RAG_RESULT_NOT_FOUND(32004, "RAG 结果不存在"),
    RAG_RESULT_INVALIDATED(32005, "RAG 结果已失效"),
    RAG_RESULT_EXPIRED(32006, "RAG 结果已过期"),
    RAG_CITATION_INVALID(32007, "RAG 引用校验失败"),
    RAG_SOURCE_CHANGED(32008, "回答生成期间知识来源发生变化"),
    RERANK_UNAVAILABLE(32009, "重排服务暂时不可用");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
