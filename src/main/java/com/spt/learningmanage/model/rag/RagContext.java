package com.spt.learningmanage.model.rag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record RagContext(
        String userPrompt,
        String safeLogSummary,
        Map<String, RagCandidate> evidence
) {
    public RagContext {
        evidence = evidence == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(evidence));
    }
}
