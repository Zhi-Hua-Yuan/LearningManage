package com.spt.learningmanage.model.dto.ai.structured;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = false)
public record WeeklyPolishStructuredResponse(
        @JsonProperty(required = true) String review
) {
}
