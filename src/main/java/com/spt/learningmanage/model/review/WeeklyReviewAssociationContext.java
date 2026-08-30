package com.spt.learningmanage.model.review;

import java.util.List;

/**
 * Server-derived association facts used by the weekly-review write path.
 * The task IDs are normalized/deduplicated and the project name is loaded
 * from the database; callers must not copy either value from untrusted input.
 */
public record WeeklyReviewAssociationContext(
        Long focusProjectId,
        String focusProjectName,
        List<Long> taskIds
) {

    public WeeklyReviewAssociationContext {
        taskIds = taskIds == null ? List.of() : List.copyOf(taskIds);
    }

    public static WeeklyReviewAssociationContext empty() {
        return new WeeklyReviewAssociationContext(null, null, List.of());
    }
}
