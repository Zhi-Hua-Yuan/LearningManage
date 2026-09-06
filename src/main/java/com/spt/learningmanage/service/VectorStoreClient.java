package com.spt.learningmanage.service;

import com.spt.learningmanage.model.dto.knowledge.VectorDocumentSnapshot;
import com.spt.learningmanage.model.dto.knowledge.VectorPayloadUpdate;
import com.spt.learningmanage.model.dto.knowledge.VectorPoint;

import java.util.List;

public interface VectorStoreClient {

    void ensureCollection();

    void upsertPoints(List<VectorPoint> points);

    void overwritePayload(List<VectorPayloadUpdate> updates);

    void deletePoints(List<String> pointIds);

    void deleteByDocumentKey(String documentKey);

    VectorDocumentSnapshot inspectByDocumentKey(String documentKey);
}
