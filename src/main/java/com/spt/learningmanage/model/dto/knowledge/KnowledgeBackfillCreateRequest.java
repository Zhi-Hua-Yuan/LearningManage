package com.spt.learningmanage.model.dto.knowledge;

import lombok.Data;

@Data
public class KnowledgeBackfillCreateRequest {
    private String runKey;
    private String runType;
    private String sourceScope;
    private Integer batchSize;
}
