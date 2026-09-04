package com.spt.learningmanage.ai.pipeline;

import com.spt.learningmanage.constant.AiCallFailureTypeEnum;

/**
 * 提供给确定性规则降级器的安全失败上下文。
 */
public record AiPipelineFailure(
        Long callLogId,
        AiCallFailureTypeEnum failureType,
        String safeMessage,
        Throwable cause
) {
}
