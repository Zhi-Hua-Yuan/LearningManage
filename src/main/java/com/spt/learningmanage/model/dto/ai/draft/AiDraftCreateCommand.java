package com.spt.learningmanage.model.dto.ai.draft;

public record AiDraftCreateCommand(
        Long userId,
        String scene,
        String payloadJson,
        String inputHash,
        Integer schemaVersion,
        String traceId,
        Integer expireMinutes
) {
    public AiDraftCreateCommand(Long userId,
                                String scene,
                                String payloadJson,
                                String inputHash,
                                Integer schemaVersion,
                                String traceId) {
        this(userId, scene, payloadJson, inputHash, schemaVersion, traceId, null);
    }
}
