package com.spt.learningmanage.model.dto.ai.chat;

public record AiFunctionCall(
        String name,
        String arguments
) {
}
