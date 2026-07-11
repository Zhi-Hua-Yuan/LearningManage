package com.spt.learningmanage.model.dto.ai;

public record AiHttpResponse(
        int statusCode,
        String responseBody
) {
}
