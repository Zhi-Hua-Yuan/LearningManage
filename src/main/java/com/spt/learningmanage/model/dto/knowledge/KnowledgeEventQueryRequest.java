package com.spt.learningmanage.model.dto.knowledge;

import lombok.Data;

@Data
public class KnowledgeEventQueryRequest {
    private String status;
    private Long current = 1L;
    private Long size = 20L;
}
