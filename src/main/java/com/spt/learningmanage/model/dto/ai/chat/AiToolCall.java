package com.spt.learningmanage.model.dto.ai.chat;

public record AiToolCall(
        String id,
        String type,
        AiFunctionCall function
) {

    public static AiToolCall function(String id, String name, String arguments) {
        return new AiToolCall(id, "function", new AiFunctionCall(name, arguments));
    }
}
