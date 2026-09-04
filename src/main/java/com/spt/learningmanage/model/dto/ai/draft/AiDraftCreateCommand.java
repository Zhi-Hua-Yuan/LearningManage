package com.spt.learningmanage.model.dto.ai.draft;

public record AiDraftCreateCommand(
        Long userId,
        String scene,
        String payloadJson,
        String inputHash,
        Integer schemaVersion,
        String traceId
) {
}
