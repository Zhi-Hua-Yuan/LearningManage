package com.spt.learningmanage.ai.governance;

public record AiSanitizedContent(
        String value,
        AiSanitizationStatus status,
        boolean truncated,
        String sha256
) {
}
