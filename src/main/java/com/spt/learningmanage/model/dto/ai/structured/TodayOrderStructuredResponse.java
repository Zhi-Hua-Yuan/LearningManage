package com.spt.learningmanage.model.dto.ai.structured;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public record TodayOrderStructuredResponse(
        @JsonProperty(required = true) String strategy,
        @JsonProperty(required = true) List<TodayOrderStructuredItem> items
) {
}
