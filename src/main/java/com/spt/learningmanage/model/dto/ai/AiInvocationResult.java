package com.spt.learningmanage.model.dto.ai;

/**
 * 一次完整 AI 调用的结果，包含兜底模型执行元数据。
 */
public record AiInvocationResult(
        String content,
        String actualModel,
        Integer retryCount
) {
}
