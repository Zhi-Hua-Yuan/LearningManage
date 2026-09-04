package com.spt.learningmanage.model.dto.ai.chat;

import java.util.List;

public record AiChatMessage(
        AiMessageRole role,
        String content,
        String toolCallId,
        List<AiToolCall> toolCalls
) {

    public AiChatMessage {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static AiChatMessage system(String content) {
        return new AiChatMessage(AiMessageRole.SYSTEM, content, null, List.of());
    }

    public static AiChatMessage user(String content) {
        return new AiChatMessage(AiMessageRole.USER, content, null, List.of());
    }

    public static AiChatMessage assistant(String content) {
        return new AiChatMessage(AiMessageRole.ASSISTANT, content, null, List.of());
    }

    public static AiChatMessage assistant(String content, List<AiToolCall> toolCalls) {
        return new AiChatMessage(AiMessageRole.ASSISTANT, content, null, toolCalls);
    }

    public static AiChatMessage tool(String toolCallId, String content) {
        return new AiChatMessage(AiMessageRole.TOOL, content, toolCallId, List.of());
    }
}
