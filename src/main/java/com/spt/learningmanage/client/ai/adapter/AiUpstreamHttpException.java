package com.spt.learningmanage.client.ai.adapter;

/**
 * 上游返回非 2xx 时抛出的传输层异常，携带**结构化**的状态码。
 *
 * <p>存在的理由：不同传输实现暴露状态码的方式不同。legacy 侧直接拿到
 * {@code AiHttpResponse.statusCode()}；Spring AI 侧则会把错误吞进
 * {@code TransientAiException} / {@code NonTransientAiException}，只留一句
 * {@code "<status> - <body>"} 的文本。若靠解析这句话来还原状态码，
 * 适配器行为就绑死在框架的消息格式上；因此这里改为在传输层装一个自己的
 * {@code ResponseErrorHandler}，把状态码作为字段带出来。</p>
 *
 * <p>本类只承载「上游回了什么状态」，不做失败分类——分类是适配器的职责，
 * 且两个适配器必须用同一套规则（408/504→超时、429→限流、5xx→上游错误、
 * 其余 4xx→被拒绝）。</p>
 *
 * <p>响应正文只作内部诊断用，绝不进入面向用户的文案
 * （与 legacy 侧「上游正文不写入异常」的口径一致）。</p>
 */
public class AiUpstreamHttpException extends RuntimeException {

    private final int statusCode;

    private final String responseBody;

    public AiUpstreamHttpException(int statusCode, String responseBody) {
        super("AI 上游返回非 2xx 状态: status=" + statusCode);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    /**
     * 上游响应正文。仅供内部日志/诊断，不得回显给调用方。
     */
    public String getResponseBody() {
        return responseBody;
    }
}
