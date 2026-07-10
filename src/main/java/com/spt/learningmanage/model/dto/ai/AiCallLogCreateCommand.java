package com.spt.learningmanage.model.dto.ai;

/**
 * 创建 AI 调用记录的内部命令对象。
 */
public record AiCallLogCreateCommand(
        Long userId,
        String scene,
        String modelName,
        String promptCode,
        Long promptTemplateId,
        Integer promptVersion,
        String promptSource,
        String requestText,
        Integer retryCount
) {
}
