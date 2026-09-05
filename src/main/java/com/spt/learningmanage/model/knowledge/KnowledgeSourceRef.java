package com.spt.learningmanage.model.knowledge;

import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;

public record KnowledgeSourceRef(KnowledgeSourceTypeEnum sourceType, Long sourceId) {
    public KnowledgeSourceRef {
        if (sourceType == null || sourceId == null || sourceId <= 0) {
            throw new IllegalArgumentException("Knowledge source reference is invalid");
        }
    }
}
