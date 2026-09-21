package com.spt.learningmanage.model.dto.ai.structured;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = false)
public record TaskBreakdownStructuredTask(
        @JsonProperty(required = true) String name,
        @JsonProperty(required = true) Integer priority,
        @JsonProperty(required = true) String dueDate
) {
}
