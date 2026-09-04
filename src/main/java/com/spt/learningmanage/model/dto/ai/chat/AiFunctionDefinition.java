package com.spt.learningmanage.model.dto.ai.chat;

import com.fasterxml.jackson.databind.JsonNode;

public record AiFunctionDefinition(
        String name,
        String description,
        JsonNode parameters
) {

    public AiFunctionDefinition {
        parameters = parameters == null ? null : parameters.deepCopy();
    }
}
