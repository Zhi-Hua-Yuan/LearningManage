package com.spt.learningmanage.model.dto.rag;

import java.util.Objects;

public record RerankCandidate(String candidateId, String text) {
    public RerankCandidate {
        Objects.requireNonNull(candidateId, "candidateId");
        Objects.requireNonNull(text, "text");
    }
}
