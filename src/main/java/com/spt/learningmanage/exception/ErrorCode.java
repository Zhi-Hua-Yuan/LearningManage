package com.spt.learningmanage.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    /**
     * 基础通用错误码 (400xx: 客户端错误, 401xx: 鉴权错误, 500xx: 服务端错误)
     */
    SUCCESS(0, "ok", HttpStatus.OK),
    PARAMS_ERROR(40000, "请求参数错误", HttpStatus.BAD_REQUEST),
    NOT_LOGIN_ERROR(40100, "未登录", HttpStatus.UNAUTHORIZED),
    NO_AUTH_ERROR(40101, "无权限", HttpStatus.FORBIDDEN),
    NOT_FOUND_ERROR(40400, "请求数据不存在", HttpStatus.NOT_FOUND),
    FORBIDDEN_ERROR(40300, "禁止访问", HttpStatus.FORBIDDEN),
    RESOURCE_STATE_CONFLICT(40901, "资源状态已发生变化", HttpStatus.CONFLICT),
    RATE_LIMIT_ERROR(42900, "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    SYSTEM_ERROR(50000, "系统内部异常", HttpStatus.INTERNAL_SERVER_ERROR),
    OPERATION_ERROR(50001, "操作失败", HttpStatus.INTERNAL_SERVER_ERROR),

    /**
     * 项目相关 (1xxxx)
     */
    PROJECT_NAME_EMPTY(10001, "项目名称不能为空", HttpStatus.BAD_REQUEST),
    PROJECT_ALREADY_EXISTS(10002, "项目已存在", HttpStatus.CONFLICT),
    PROJECT_NOT_FOUND(10003, "项目不存在", HttpStatus.NOT_FOUND),

    /**
     * 用户相关 (2xxxx)
     */
    USER_NOT_FOUND(20001, "用户不存在", HttpStatus.NOT_FOUND),
    ACCOUNT_ALREADY_EXISTS(20002, "账号已存在", HttpStatus.CONFLICT),
    PASSWORD_ERROR(20003, "密码错误", HttpStatus.BAD_REQUEST),

    /** 团队生命周期相关 (41xxx). */
    TEAM_HAS_BUSINESS_DATA(41001, "团队仍有关联业务数据", HttpStatus.CONFLICT),
    TEAM_STATE_CONFLICT(41002, "团队状态已发生变化", HttpStatus.CONFLICT),

    /**
     * AI 调用相关 (3xxxx)
     */
    AI_SERVICE_UNAVAILABLE(30001, "AI 服务暂时不可用", HttpStatus.SERVICE_UNAVAILABLE),
    AI_REQUEST_TIMEOUT(30002, "AI 服务响应超时", HttpStatus.SERVICE_UNAVAILABLE),
    AI_RESPONSE_INVALID(30003, "AI 返回结果格式异常", HttpStatus.SERVICE_UNAVAILABLE),
    AI_CONFIG_ERROR(30004, "AI 服务配置异常", HttpStatus.SERVICE_UNAVAILABLE),
    AI_DRAFT_NOT_CONFIRMABLE(30005, "AI 草稿当前不可确认", HttpStatus.CONFLICT),
    AI_DRAFT_EXPIRED(30006, "AI 草稿已过期", HttpStatus.CONFLICT),
    AI_DRAFT_SCHEMA_UNSUPPORTED(30007, "AI 草稿版本不受支持", HttpStatus.BAD_REQUEST),
    AI_DRAFT_CONFLICT(30008, "AI 草稿状态冲突", HttpStatus.CONFLICT),
    AI_DISABLED(30009, "AI 生成功能已关闭", HttpStatus.SERVICE_UNAVAILABLE),
    AI_CONCURRENCY_LIMIT(30010, "AI 服务当前请求较多", HttpStatus.TOO_MANY_REQUESTS),
    AI_CONTENT_BLOCKED(30011, "请求包含禁止发送的敏感信息", HttpStatus.BAD_REQUEST),

    /** Knowledge index operations (31xxx). */
    KNOWLEDGE_INDEX_DISABLED(31001, "知识索引服务未启用", HttpStatus.SERVICE_UNAVAILABLE),
    KNOWLEDGE_EVENT_NOT_FOUND(31002, "知识索引事件不存在", HttpStatus.NOT_FOUND),
    KNOWLEDGE_EVENT_NOT_REPLAYABLE(31003, "知识索引事件不可重放", HttpStatus.CONFLICT),
    KNOWLEDGE_BACKFILL_NOT_FOUND(31004, "知识索引回填任务不存在", HttpStatus.NOT_FOUND),
    KNOWLEDGE_BACKFILL_CONFLICT(31005, "知识索引回填任务冲突", HttpStatus.CONFLICT),
    EMBEDDING_UNAVAILABLE(31006, "Embedding 服务暂时不可用", HttpStatus.SERVICE_UNAVAILABLE),
    EMBEDDING_DIMENSION_MISMATCH(31007, "Embedding 向量维度不匹配", HttpStatus.SERVICE_UNAVAILABLE),
    VECTOR_STORE_UNAVAILABLE(31008, "向量库暂时不可用", HttpStatus.SERVICE_UNAVAILABLE),
    VECTOR_COLLECTION_INVALID(31009, "向量集合配置不合法", HttpStatus.SERVICE_UNAVAILABLE),

    /** Permission-aware RAG (32xxx). */
    RAG_DISABLED(32001, "RAG 问答功能未启用", HttpStatus.SERVICE_UNAVAILABLE),
    KNOWLEDGE_INDEX_NOT_READY(32002, "知识索引尚未就绪", HttpStatus.SERVICE_UNAVAILABLE),
    RAG_DEPENDENCY_UNAVAILABLE(32003, "RAG 依赖服务暂时不可用", HttpStatus.SERVICE_UNAVAILABLE),
    RAG_RESULT_NOT_FOUND(32004, "RAG 结果不存在", HttpStatus.NOT_FOUND),
    RAG_RESULT_INVALIDATED(32005, "RAG 结果已失效", HttpStatus.CONFLICT),
    RAG_RESULT_EXPIRED(32006, "RAG 结果已过期", HttpStatus.CONFLICT),
    RAG_CITATION_INVALID(32007, "RAG 引用校验失败", HttpStatus.SERVICE_UNAVAILABLE),
    RAG_SOURCE_CHANGED(32008, "回答生成期间知识来源发生变化", HttpStatus.CONFLICT),
    RERANK_UNAVAILABLE(32009, "重排服务暂时不可用", HttpStatus.SERVICE_UNAVAILABLE),

    /** Controlled asynchronous Agent and reports (33xxx). */
    AGENT_DISABLED(33001, "Agent 功能未启用", HttpStatus.SERVICE_UNAVAILABLE),
    AGENT_QUEUE_FULL(33002, "Agent 队列已满", HttpStatus.TOO_MANY_REQUESTS),
    AGENT_CONCURRENCY_LIMIT(33003, "Agent 并发数量已达上限", HttpStatus.TOO_MANY_REQUESTS),
    AGENT_RUN_NOT_FOUND(33004, "Agent 运行不存在", HttpStatus.NOT_FOUND),
    AGENT_RUN_NOT_FINISHED(33005, "Agent 运行尚未完成", HttpStatus.CONFLICT),
    AGENT_RUN_ALREADY_FINISHED(33006, "Agent 运行已经结束", HttpStatus.CONFLICT),
    AGENT_TIMEOUT(33007, "Agent 运行超时", HttpStatus.SERVICE_UNAVAILABLE),
    AGENT_CANCELED(33008, "Agent 运行已取消", HttpStatus.CONFLICT),
    AGENT_WORKER_LOST(33009, "Agent Worker 执行中断", HttpStatus.SERVICE_UNAVAILABLE),
    AGENT_DATA_CHANGED(33010, "Agent 分析期间业务数据发生变化", HttpStatus.CONFLICT),
    AGENT_REPORT_STALE(33011, "Agent 报告所依据的数据已变化", HttpStatus.CONFLICT),
    TOOL_NOT_ALLOWED(33012, "Agent Tool 不允许调用", HttpStatus.FORBIDDEN),
    TOOL_ARGUMENT_INVALID(33013, "Agent Tool 参数不合法", HttpStatus.BAD_REQUEST),
    TOOL_CALL_LIMIT_EXCEEDED(33014, "Agent Tool 调用次数超限", HttpStatus.TOO_MANY_REQUESTS),
    TOOL_EXECUTION_FAILED(33015, "Agent Tool 执行失败", HttpStatus.SERVICE_UNAVAILABLE),
    REPORT_NOT_FOUND(33016, "分析报告不存在", HttpStatus.NOT_FOUND),
    REPORT_NO_ACCESS(33017, "无权访问分析报告", HttpStatus.FORBIDDEN),
    REPORT_ALREADY_DELETED(33018, "分析报告已删除", HttpStatus.CONFLICT),

    /** Stage 7 production operations and data lifecycle (34xxx). */
    OPS_TIME_RANGE_INVALID(34001, "运维查询时间范围不合法", HttpStatus.BAD_REQUEST),
    OPS_DATA_UNAVAILABLE(34002, "运维数据暂不可用", HttpStatus.SERVICE_UNAVAILABLE),
    CLEANUP_DISABLED(34003, "数据清理功能未启用", HttpStatus.SERVICE_UNAVAILABLE),
    CLEANUP_ALREADY_RUNNING(34004, "已有数据清理任务正在运行", HttpStatus.CONFLICT),
    CLEANUP_RUN_NOT_FOUND(34005, "数据清理任务不存在", HttpStatus.NOT_FOUND),
    CLEANUP_DRY_RUN_REQUIRED(34006, "正式清理前必须完成匹配的预演", HttpStatus.CONFLICT),
    CLEANUP_ESTIMATE_CHANGED(34007, "清理预计数量发生显著变化", HttpStatus.CONFLICT),
    CLEANUP_RUN_NOT_CANCELABLE(34008, "数据清理任务当前不可取消", HttpStatus.CONFLICT),
    DEPENDENCY_STATUS_UNAVAILABLE(34009, "依赖状态暂不可获取", HttpStatus.SERVICE_UNAVAILABLE);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
