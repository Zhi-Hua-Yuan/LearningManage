package com.spt.learningmanage.ai.pipeline;

import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.model.dto.ai.chat.AiChatMessage;
import com.spt.learningmanage.model.dto.ai.chat.AiToolChoice;
import com.spt.learningmanage.model.dto.ai.chat.AiToolDefinition;

import java.util.List;

public record AiChatRoundExecutionCommand(
        Long userId,
        String modelName,
        AiPromptCodeEnum promptCode,
        List<AiChatMessage> messages,
        List<AiToolDefinition> tools,
        AiToolChoice toolChoice,
        Double temperature,
        Integer maxOutputTokens,
        String traceId,
        String requestLogSummary,
        String agentRunId,
        Integer agentRoundNo
) {
    public AiChatRoundExecutionCommand {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        if (userId == null || userId <= 0 || modelName == null || modelName.isBlank()
                || promptCode == null || messages.isEmpty()) {
            throw new IllegalArgumentException("Agent 模型轮次参数不合法");
        }
        if (requestLogSummary == null || requestLogSummary.isBlank()) {
            throw new IllegalArgumentException("Agent 模型轮次必须提供安全日志摘要");
        }
        if (agentRunId == null || agentRunId.isBlank() || agentRoundNo == null || agentRoundNo <= 0) {
            throw new IllegalArgumentException("Agent Run 关联参数不合法");
        }
    }
}
