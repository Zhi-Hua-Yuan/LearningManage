package com.spt.learningmanage.service.ai.draft;

import com.spt.learningmanage.model.dto.ai.draft.AiDraftConfirmationContext;

public interface AiDraftHandlerRegistry {

    AiDraftHandler<?> require(String scene, AiDraftConfirmationContext context);

    int currentSchemaVersion(String scene);
}
