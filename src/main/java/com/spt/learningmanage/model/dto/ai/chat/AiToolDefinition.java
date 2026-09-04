package com.spt.learningmanage.model.dto.ai.chat;

public record AiToolDefinition(
        String type,
        AiFunctionDefinition function
) {

    public static AiToolDefinition function(AiFunctionDefinition function) {
        return new AiToolDefinition("function", function);
    }
}
