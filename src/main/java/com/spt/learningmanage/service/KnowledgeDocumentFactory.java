package com.spt.learningmanage.service;

import com.spt.learningmanage.model.knowledge.KnowledgeDocumentProjection;
import com.spt.learningmanage.model.knowledge.KnowledgeSourceRef;

import java.util.List;

public interface KnowledgeDocumentFactory {

    List<KnowledgeDocumentProjection> buildDesiredDocuments(KnowledgeSourceRef source);
}
