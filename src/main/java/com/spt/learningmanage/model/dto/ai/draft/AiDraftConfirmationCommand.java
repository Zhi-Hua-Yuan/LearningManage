package com.spt.learningmanage.model.dto.ai.draft;

public record AiDraftConfirmationCommand(
        Long userId,
        String draftId,
        String operationId,
        String scene,
        AiDraftConfirmationContext context
) {
}
