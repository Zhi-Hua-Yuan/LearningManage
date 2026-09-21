package com.spt.learningmanage.model.dto.ai.structured;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = false)
public record DailyRenameStructuredItem(
        @JsonProperty(required = true) Long taskId,
        @JsonProperty(required = true) String newTitle,
        @JsonProperty(required = true) String reason,
        @JsonProperty(required = true) Integer confidence
) {
}
