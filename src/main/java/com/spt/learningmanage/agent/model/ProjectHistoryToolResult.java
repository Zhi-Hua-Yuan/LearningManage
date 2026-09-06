package com.spt.learningmanage.agent.model;

import java.util.List;

public record ProjectHistoryToolResult(List<ProjectHistoryEvidence> evidence,
                                       boolean degraded,
                                       String degradationReason) {
    public ProjectHistoryToolResult {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
