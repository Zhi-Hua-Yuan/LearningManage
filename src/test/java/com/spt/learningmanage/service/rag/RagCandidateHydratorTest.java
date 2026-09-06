package com.spt.learningmanage.service.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.constant.KnowledgeEventTypeEnum;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.constant.KnowledgeVisibilityTypeEnum;
import com.spt.learningmanage.model.dto.knowledge.VectorSearchHit;
import com.spt.learningmanage.model.knowledge.KnowledgeDocumentProjection;
import com.spt.learningmanage.model.knowledge.KnowledgeSourceRef;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.service.KnowledgeDocumentFactory;
import com.spt.learningmanage.service.KnowledgeIndexEventPublisher;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.knowledge.KnowledgeChunker;
import com.spt.learningmanage.service.knowledge.KnowledgeHashing;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagCandidateHydratorTest {
    private final KnowledgeDocumentFactory factory = mock(KnowledgeDocumentFactory.class);
    private final KnowledgeIndexEventPublisher publisher = mock(KnowledgeIndexEventPublisher.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final KnowledgeHashing hashing = new KnowledgeHashing(new ObjectMapper());
    private final RagCandidateHydrator hydrator = new RagCandidateHydrator(
            factory, new KnowledgeChunker(), hashing, publisher, permissionService);

    @Test
    void forgedPrivateOwnerIsRejectedBeforeAnyDatabaseHydration() {
        Map<String, Object> payload = payload(8L, "x:y");
        var result = hydrator.hydrate(7L, personalScope(),
                List.of(new VectorSearchHit("p1", 0.9, payload)));

        assertEquals(List.of(), result);
        verify(factory, never()).buildDesiredDocuments(any());
    }

    @Test
    void validCurrentSourceIsRebuiltFromBusinessProjection() {
        KnowledgeDocumentProjection projection = projection();
        String version = hashing.contentHash(projection.canonicalText()) + ':'
                + hashing.payloadHash(projection.payload());
        when(factory.buildDesiredDocuments(new KnowledgeSourceRef(KnowledgeSourceTypeEnum.TASK, 20L)))
                .thenReturn(List.of(projection));
        when(permissionService.filterReadableTaskIds(7L, List.of(20L))).thenReturn(java.util.Set.of(20L));

        var result = hydrator.hydrate(7L, personalScope(), List.of(
                new VectorSearchHit("p1", 0.9, payload(7L, version))));

        assertEquals(1, result.size());
        assertEquals("实现RAG", result.get(0).title());
        assertEquals("任务标题: 实现RAG\n完成权限检索", result.get(0).text());
        verify(publisher, never()).publish(any(), any(), any());
    }

    @Test
    void staleSourceVersionIsDroppedAndSchedulesCorrection() {
        KnowledgeDocumentProjection projection = projection();
        when(factory.buildDesiredDocuments(new KnowledgeSourceRef(KnowledgeSourceTypeEnum.TASK, 20L)))
                .thenReturn(List.of(projection));
        when(permissionService.filterReadableTaskIds(7L, List.of(20L))).thenReturn(java.util.Set.of(20L));

        var result = hydrator.hydrate(7L, personalScope(), List.of(
                new VectorSearchHit("p1", 0.9, payload(7L, "old:version"))));

        assertEquals(List.of(), result);
        verify(publisher).publish(KnowledgeSourceTypeEnum.TASK, 20L,
                KnowledgeEventTypeEnum.SOURCE_CHANGED);
    }

    private ProjectAccessScope personalScope() {
        return new ProjectAccessScope(7L, 10L, 7L, null, null);
    }

    private KnowledgeDocumentProjection projection() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("sourceType", "TASK");
        values.put("sourceId", 20L);
        values.put("projectId", 10L);
        values.put("userId", 7L);
        values.put("ownerUserId", 7L);
        values.put("visibilityType", "PRIVATE");
        return new KnowledgeDocumentProjection(
                "TASK:20:PRIVATE:10", KnowledgeSourceTypeEnum.TASK, 20L, 10L,
                null, 7L, KnowledgeVisibilityTypeEnum.PRIVATE,
                "任务标题: 实现RAG", "完成权限检索", values);
    }

    private Map<String, Object> payload(Long ownerId, String sourceVersion) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceType", "TASK");
        payload.put("sourceId", 20L);
        payload.put("projectId", 10L);
        payload.put("ownerUserId", ownerId);
        payload.put("visibilityType", "PRIVATE");
        payload.put("documentKey", "TASK:20:PRIVATE:10");
        payload.put("chunkIndex", 0);
        payload.put("sourceVersion", sourceVersion);
        return payload;
    }
}
