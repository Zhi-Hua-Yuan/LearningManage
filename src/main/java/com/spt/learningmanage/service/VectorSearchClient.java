package com.spt.learningmanage.service;

import com.spt.learningmanage.model.dto.knowledge.VectorSearchHit;
import com.spt.learningmanage.model.dto.knowledge.VectorSearchRequest;

import java.util.List;

public interface VectorSearchClient {
    List<VectorSearchHit> query(VectorSearchRequest request);
}
