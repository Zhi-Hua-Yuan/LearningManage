package com.spt.learningmanage.model.rag;

import java.util.List;

public record RagAnswerContent(
        String answer,
        boolean insufficientEvidence,
        List<String> citations
) {
    public RagAnswerContent {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
