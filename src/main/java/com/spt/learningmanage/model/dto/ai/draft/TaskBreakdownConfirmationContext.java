package com.spt.learningmanage.model.dto.ai.draft;

public record TaskBreakdownConfirmationContext(
        String projectName,
        String projectGoal
) implements AiDraftConfirmationContext {
}
