package com.spt.learningmanage.model.knowledge;

import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.constant.KnowledgeVisibilityTypeEnum;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;

public record KnowledgeDocumentProjection(
        String documentKey,
        KnowledgeSourceTypeEnum sourceType,
        Long sourceId,
        Long projectId,
        Long teamId,
        Long ownerUserId,
        KnowledgeVisibilityTypeEnum visibilityType,
        String repeatPrefix,
        String semanticBody,
        Map<String, Object> payload
) {
    public KnowledgeDocumentProjection {
        payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    public String canonicalText() {
        if (repeatPrefix == null || repeatPrefix.isBlank()) {
            return semanticBody == null ? "" : semanticBody;
        }
        if (semanticBody == null || semanticBody.isBlank()) {
            return repeatPrefix;
        }
        return repeatPrefix + "\n" + semanticBody;
    }
}
