package com.spt.learningmanage.model.dto.ai.structured;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = false)
public record ListReplanStructuredItem(
        @JsonProperty(required = true) Long taskId,
        @JsonProperty(required = true) String newTitle,
        @JsonProperty(required = true) Integer newPriority,
        @JsonProperty(required = true) LocalDate newDueDate,
        @JsonProperty(required = true) Integer confidence,
        @JsonProperty(required = true) String reason
) {
}
