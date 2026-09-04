package com.spt.learningmanage.ai.pipeline;

/**
 * AI 调用或响应处理失败后的确定性业务降级。
 */
@FunctionalInterface
public interface AiFallback<T> {

    T apply(AiPipelineFailure failure);
}
