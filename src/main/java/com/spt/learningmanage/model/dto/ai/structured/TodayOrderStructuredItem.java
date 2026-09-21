package com.spt.learningmanage.model.dto.ai.structured;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = false)
public record TodayOrderStructuredItem(
        @JsonProperty(required = true) Long taskId,
        @JsonProperty(required = true) Integer difficulty,
        @JsonProperty(required = true) Integer cost,
        @JsonProperty(required = true) Integer benefit,
        @JsonProperty(required = true) Integer estimatedMinutes,
        @JsonProperty(required = true) String reason
) {
}
