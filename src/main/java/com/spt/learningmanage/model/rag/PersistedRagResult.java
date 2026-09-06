package com.spt.learningmanage.model.rag;

import com.spt.learningmanage.model.entity.AiRagResult;
import com.spt.learningmanage.model.entity.AiRagResultSource;

import java.util.List;

public record PersistedRagResult(AiRagResult result, List<AiRagResultSource> sources) {
    public PersistedRagResult {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
