package com.spt.learningmanage.service;

import com.spt.learningmanage.model.knowledge.IndexExecutionContext;
import com.spt.learningmanage.model.knowledge.KnowledgeSourceRef;

public interface KnowledgeIndexService {

    void reconcileSource(KnowledgeSourceRef source, IndexExecutionContext context);
}
