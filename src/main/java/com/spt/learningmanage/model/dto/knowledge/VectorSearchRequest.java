package com.spt.learningmanage.model.dto.knowledge;

import java.util.List;
import java.util.Objects;

public record VectorSearchRequest(
        List<Float> vector,
        VectorAccessFilter accessFilter,
        int limit,
        double scoreThreshold
) {
    public VectorSearchRequest {
        vector = vector == null ? List.of() : List.copyOf(vector);
        Objects.requireNonNull(accessFilter, "accessFilter");
        if (vector.isEmpty()) {
            throw new IllegalArgumentException("query vector must not be empty");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("query limit must be between 1 and 100");
        }
        if (!Double.isFinite(scoreThreshold)) {
            throw new IllegalArgumentException("score threshold must be finite");
        }
    }
}
