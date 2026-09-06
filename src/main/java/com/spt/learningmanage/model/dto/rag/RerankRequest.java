package com.spt.learningmanage.model.dto.rag;

import java.util.List;

public record RerankRequest(
        String query,
        List<RerankCandidate> candidates,
        int topN,
        String traceId
) {
    public RerankRequest {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("rerank query must not be blank");
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("rerank candidates must not be empty");
        }
        if (topN < 1 || topN > candidates.size()) {
            throw new IllegalArgumentException("rerank topN must be within candidate count");
        }
    }
}
