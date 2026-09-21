package com.spt.learningmanage.model.dto.ai.structured;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public record TaskBreakdownStructuredResponse(
        @JsonProperty(required = true) List<TaskBreakdownStructuredMilestone> milestones
) {
}
