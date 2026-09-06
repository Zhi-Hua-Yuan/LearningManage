package com.spt.learningmanage.model.dto.knowledge;

import java.util.Objects;

/** Server-derived access filter. Never bind this type from an HTTP request. */
public record VectorAccessFilter(
        Long projectId,
        Long actorUserId,
        Long teamId
) {
    public VectorAccessFilter {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(actorUserId, "actorUserId");
    }

    public boolean teamProject() {
        return teamId != null;
    }
}
