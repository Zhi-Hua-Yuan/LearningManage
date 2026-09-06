package com.spt.learningmanage.agent.model;

import java.util.List;

public record TeamManagerAnalysis(String managerSummary, List<String> recommendations) {
    public TeamManagerAnalysis {
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
    }
}
