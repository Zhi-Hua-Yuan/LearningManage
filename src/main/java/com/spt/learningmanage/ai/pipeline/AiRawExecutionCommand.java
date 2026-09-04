package com.spt.learningmanage.ai.pipeline;

import java.util.regex.Pattern;

/**
 * 仅用于兼容既有内部通用 chat 的原始 Prompt 命令。
 */
public record AiRawExecutionCommand(
        Long userId,
        String modelName,
        String systemPrompt,
        String userPrompt,
        String parseFailureMessage,
        String traceId
) {
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    public AiRawExecutionCommand {
        if (userId != null && userId <= 0) {
            throw new IllegalArgumentException("用户 ID 必须为正整数");
        }
        requireText(modelName, "模型名称不能为空");
        requireText(systemPrompt, "系统提示词不能为空");
        requireText(userPrompt, "用户提示词不能为空");
        requireText(parseFailureMessage, "响应解析失败提示不能为空");
        validateTraceId(traceId);
    }

    static void validateTraceId(String traceId) {
        if (traceId != null && !TRACE_ID_PATTERN.matcher(traceId).matches()) {
            throw new IllegalArgumentException("traceId 格式不合法");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
