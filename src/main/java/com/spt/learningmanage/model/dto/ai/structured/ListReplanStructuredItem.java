package com.spt.learningmanage.model.dto.ai.structured;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = false)
public record ListReplanStructuredItem(
        @JsonProperty(required = true) Long taskId,
        @JsonProperty(required = true) String newTitle,
        @JsonProperty(required = true) Integer newPriority,
        // Keep the provider token as text so the scene-level normalizer can
        // safely retain the original date when a model returns an invalid one.
        @JsonProperty(required = true) String newDueDate,
        @JsonProperty(required = true) Integer confidence,
        @JsonProperty(required = true) String reason
) {
}
