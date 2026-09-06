package com.spt.learningmanage.agent.model;

import java.util.List;

public record ProjectRiskAnalysis(String riskLevel,
                                  String summary,
                                  List<AgentRiskItem> riskItems,
                                  List<String> positiveSignals,
                                  boolean insufficientEvidence,
                                  List<String> citations) {
    public ProjectRiskAnalysis {
        riskItems = riskItems == null ? List.of() : List.copyOf(riskItems);
        positiveSignals = positiveSignals == null ? List.of() : List.copyOf(positiveSignals);
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
