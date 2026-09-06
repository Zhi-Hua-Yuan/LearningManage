package com.spt.learningmanage.agent.model;

import java.util.List;

public record TeamWorkloadToolResult(List<TeamMemberWorkloadMetric> members) {
    public TeamWorkloadToolResult {
        members = members == null ? List.of() : List.copyOf(members);
    }
}
