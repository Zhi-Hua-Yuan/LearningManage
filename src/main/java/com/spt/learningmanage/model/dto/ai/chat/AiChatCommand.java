package com.spt.learningmanage.model.dto.ai.chat;

import java.util.List;

public record AiChatCommand(
        String requestedModel,
        List<AiChatMessage> messages,
        List<AiToolDefinition> tools,
        AiToolChoice toolChoice,
        Double temperature,
        Integer maxOutputTokens
) {

    public AiChatCommand {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    public AiChatCommand withRequestedModel(String model) {
        return new AiChatCommand(model, messages, tools, toolChoice, temperature, maxOutputTokens);
    }
}
