package com.spt.learningmanage.agent.model;

import java.util.List;

public record AgentRiskItem(String category,
                            String severity,
                            String reason,
                            String impact,
                            String recommendation,
                            List<String> evidenceIds) {
    public AgentRiskItem {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}

