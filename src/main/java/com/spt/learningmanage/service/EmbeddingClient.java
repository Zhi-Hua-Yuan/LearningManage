package com.spt.learningmanage.service;

import com.spt.learningmanage.model.dto.knowledge.EmbeddingBatchResult;
import com.spt.learningmanage.model.dto.knowledge.EmbeddingCallContext;

import java.util.List;

public interface EmbeddingClient {

    EmbeddingBatchResult embedDocuments(List<String> texts, EmbeddingCallContext context);
}
