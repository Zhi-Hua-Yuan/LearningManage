package com.spt.learningmanage.service;

import com.spt.learningmanage.model.dto.rag.RerankRequest;
import com.spt.learningmanage.model.dto.rag.RerankResult;

public interface RerankClient {
    RerankResult rerank(RerankRequest request);
}
