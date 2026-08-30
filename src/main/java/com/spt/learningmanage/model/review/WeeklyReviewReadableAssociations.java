package com.spt.learningmanage.model.review;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Current-permission projection of persisted weekly-review associations.
 * The database relations are not changed while this object is assembled.
 */
public record WeeklyReviewReadableAssociations(
        Map<Long, List<Long>> taskIdsByReview,
        Set<Long> readableFocusProjectIds
) {

    public WeeklyReviewReadableAssociations {
        if (taskIdsByReview == null) {
            taskIdsByReview = Collections.emptyMap();
        } else {
            Map<Long, List<Long>> copied = new LinkedHashMap<>();
            taskIdsByReview.forEach((reviewId, taskIds) -> copied.put(
                    reviewId, taskIds == null ? List.of() : List.copyOf(new ArrayList<>(taskIds))));
            taskIdsByReview = Collections.unmodifiableMap(copied);
        }
        readableFocusProjectIds = readableFocusProjectIds == null ? Collections.emptySet()
                : Collections.unmodifiableSet(Set.copyOf(readableFocusProjectIds));
    }

    public static WeeklyReviewReadableAssociations empty() {
        return new WeeklyReviewReadableAssociations(Collections.emptyMap(), Collections.emptySet());
    }

    public List<Long> taskIdsFor(Long reviewId) {
        return taskIdsByReview.getOrDefault(reviewId, List.of());
    }

    public boolean canReadFocusProject(Long projectId) {
        return projectId != null && readableFocusProjectIds.contains(projectId);
    }
}
