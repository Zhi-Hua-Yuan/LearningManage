package com.spt.learningmanage.model.rag;

import java.util.List;

public record RagRetrievalOutcome(
        List<RagCandidate> candidates,
        int vectorCandidateCount,
        int authorizedCandidateCount,
        boolean degraded,
        String degradationReason,
        String embeddingModel,
        String rerankModel
) {
    public RagRetrievalOutcome {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
