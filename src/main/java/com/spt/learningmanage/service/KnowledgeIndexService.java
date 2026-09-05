package com.spt.learningmanage.service;

import com.spt.learningmanage.model.knowledge.IndexExecutionContext;
import com.spt.learningmanage.model.knowledge.KnowledgeSourceRef;
import com.spt.learningmanage.constant.KnowledgeFailureTypeEnum;

public interface KnowledgeIndexService {

    void reconcileSource(KnowledgeSourceRef source, IndexExecutionContext context);

    void markFailure(KnowledgeSourceRef source,
                     IndexExecutionContext context,
                     KnowledgeFailureTypeEnum failureType,
                     String safeError);
}
