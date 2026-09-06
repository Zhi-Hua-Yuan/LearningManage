package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.config.EmbeddingProperties;
import com.spt.learningmanage.constant.KnowledgeDocumentStatusEnum;
import com.spt.learningmanage.constant.KnowledgeEventTypeEnum;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.constant.KnowledgeVisibilityTypeEnum;
import com.spt.learningmanage.mapper.AiKnowledgeDocumentMapper;
import com.spt.learningmanage.model.dto.knowledge.EmbeddingBatchResult;
import com.spt.learningmanage.model.dto.knowledge.VectorPoint;
import com.spt.learningmanage.model.entity.AiKnowledgeDocument;
import com.spt.learningmanage.model.knowledge.IndexExecutionContext;
import com.spt.learningmanage.model.knowledge.KnowledgeDocumentProjection;
import com.spt.learningmanage.model.knowledge.KnowledgeSourceRef;
import com.spt.learningmanage.service.KnowledgeDocumentFactory;
import com.spt.learningmanage.service.KnowledgeEmbeddingService;
import com.spt.learningmanage.service.VectorStoreClient;
import com.spt.learningmanage.service.knowledge.DeterministicPointIdFactory;
import com.spt.learningmanage.service.knowledge.KnowledgeChunker;
import com.spt.learningmanage.service.knowledge.KnowledgeHashing;
import com.spt.learningmanage.service.knowledge.KnowledgeRecoveryEventService;
import com.spt.learningmanage.service.knowledge.KnowledgeSourceLeaseService;
import com.spt.learningmanage.service.knowledge.KnowledgeVectorStoreManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeIndexServiceImplTest {

    @Test
    void newDocumentEmbedsAndUpsertsDeterministicPoint() {
        Fixture fixture = new Fixture();
        KnowledgeDocumentProjection projection = fixture.projection(Map.of("status", "0"));
        when(fixture.factory.buildDesiredDocuments(fixture.source)).thenReturn(List.of(projection));
        when(fixture.documentMapper.selectBySource("TASK", 1L)).thenReturn(List.of());
        when(fixture.documentMapper.insert(any(AiKnowledgeDocument.class))).thenReturn(1);
        when(fixture.documentMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        when(fixture.embeddingService.embedDocuments(anyList(), any())).thenReturn(
                new EmbeddingBatchResult(List.of(List.of(0.1f, 0.2f, 0.3f)),
                        "text-embedding-v4", 3L, 3L, "request"));

        fixture.service().reconcileSource(fixture.source, fixture.context);

        ArgumentCaptor<List<VectorPoint>> points = ArgumentCaptor.forClass(List.class);
        verify(fixture.vectorStoreClient).upsertPoints(points.capture());
        assertEquals(1, points.getValue().size());
        Map<String, Object> payload = points.getValue().get(0).payload();
        assertEquals(4L, payload.get("userId"));
        assertTrue(payload.containsKey("sourceVersion"));
        assertTrue(payload.containsKey("updatedAt"));
        verify(fixture.vectorStoreClient, never()).overwritePayload(anyList());
    }

    @Test
    void payloadOnlyChangeDoesNotCallEmbedding() {
        Fixture fixture = new Fixture();
        KnowledgeDocumentProjection projection = fixture.projection(Map.of("status", "1"));
        KnowledgeHashing hashing = fixture.hashing;
        AiKnowledgeDocument existing = new AiKnowledgeDocument();
        existing.setId(9L);
        existing.setDocumentKey(projection.documentKey());
        existing.setSourceType("TASK");
        existing.setSourceId(1L);
        existing.setStatus(KnowledgeDocumentStatusEnum.INDEXED.name());
        existing.setChunkCount(1);
        existing.setIndexedContentHash(hashing.contentHash(projection.canonicalText()));
        existing.setIndexedPayloadHash(hashing.payloadHash(Map.of("status", "0")));
        when(fixture.factory.buildDesiredDocuments(fixture.source)).thenReturn(List.of(projection));
        when(fixture.documentMapper.selectBySource("TASK", 1L)).thenReturn(List.of(existing));
        when(fixture.documentMapper.updateById(existing)).thenReturn(1);
        when(fixture.documentMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        fixture.service().reconcileSource(fixture.source, fixture.context);

        verify(fixture.embeddingService, never()).embedDocuments(anyList(), any());
        verify(fixture.vectorStoreClient).overwritePayload(anyList());
        verify(fixture.vectorStoreClient, never()).upsertPoints(anyList());
    }

    @Test
    void shorterReembeddedDocumentDeletesAllObsoletePointIds() {
        Fixture fixture = new Fixture();
        KnowledgeDocumentProjection projection = fixture.projection(Map.of("status", "0"));
        AiKnowledgeDocument existing = new AiKnowledgeDocument();
        existing.setId(9L);
        existing.setDocumentKey(projection.documentKey());
        existing.setSourceType("TASK");
        existing.setSourceId(1L);
        existing.setStatus(KnowledgeDocumentStatusEnum.INDEXED.name());
        existing.setChunkCount(3);
        existing.setIndexedContentHash("0".repeat(64));
        existing.setIndexedPayloadHash(fixture.hashing.payloadHash(projection.payload()));
        when(fixture.factory.buildDesiredDocuments(fixture.source)).thenReturn(List.of(projection));
        when(fixture.documentMapper.selectBySource("TASK", 1L)).thenReturn(List.of(existing));
        when(fixture.documentMapper.updateById(existing)).thenReturn(1);
        when(fixture.documentMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        when(fixture.embeddingService.embedDocuments(anyList(), any())).thenReturn(
                new EmbeddingBatchResult(List.of(List.of(0.1f, 0.2f, 0.3f)),
                        "text-embedding-v4", 3L, 3L, "request"));

        fixture.service().reconcileSource(fixture.source, fixture.context);

        ArgumentCaptor<List> deleted = ArgumentCaptor.forClass(List.class);
        verify(fixture.vectorStoreClient).deletePoints(deleted.capture());
        assertEquals(2, deleted.getValue().size());
    }

    @Test
    void rebuildEventRecreatesVectorEvenWhenMetadataHashesAlreadyMatch() {
        Fixture fixture = new Fixture();
        KnowledgeDocumentProjection projection = fixture.projection(Map.of("status", "0"));
        AiKnowledgeDocument existing = new AiKnowledgeDocument();
        existing.setId(9L);
        existing.setDocumentKey(projection.documentKey());
        existing.setSourceType("TASK");
        existing.setSourceId(1L);
        existing.setStatus(KnowledgeDocumentStatusEnum.INDEXED.name());
        existing.setChunkCount(1);
        existing.setIndexedContentHash(fixture.hashing.contentHash(projection.canonicalText()));
        existing.setIndexedPayloadHash(fixture.hashing.payloadHash(projection.payload()));
        when(fixture.factory.buildDesiredDocuments(fixture.source)).thenReturn(List.of(projection));
        when(fixture.documentMapper.selectBySource("TASK", 1L)).thenReturn(List.of(existing));
        when(fixture.documentMapper.updateById(existing)).thenReturn(1);
        when(fixture.documentMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        when(fixture.embeddingService.embedDocuments(anyList(), any())).thenReturn(
                new EmbeddingBatchResult(List.of(List.of(0.1f, 0.2f, 0.3f)),
                        "text-embedding-v4", 3L, 3L, "request"));

        fixture.service().reconcileSource(fixture.source,
                new IndexExecutionContext(2L, "token", "trace-id", KnowledgeEventTypeEnum.REBUILD));

        verify(fixture.embeddingService).embedDocuments(anyList(), any());
        verify(fixture.vectorStoreClient).upsertPoints(anyList());
    }

    private static final class Fixture {
        private final KnowledgeSourceRef source = new KnowledgeSourceRef(KnowledgeSourceTypeEnum.TASK, 1L);
        private final IndexExecutionContext context = new IndexExecutionContext(
                2L, "token", "trace-id", KnowledgeEventTypeEnum.SOURCE_CHANGED);
        private final KnowledgeDocumentFactory factory = mock(KnowledgeDocumentFactory.class);
        private final AiKnowledgeDocumentMapper documentMapper = mock(AiKnowledgeDocumentMapper.class);
        private final KnowledgeEmbeddingService embeddingService = mock(KnowledgeEmbeddingService.class);
        private final VectorStoreClient vectorStoreClient = mock(VectorStoreClient.class);
        private final KnowledgeSourceLeaseService leaseService = mock(KnowledgeSourceLeaseService.class);
        private final KnowledgeRecoveryEventService recovery = mock(KnowledgeRecoveryEventService.class);
        private final KnowledgeVectorStoreManager vectorStoreManager = mock(KnowledgeVectorStoreManager.class);
        private final EmbeddingProperties properties = new EmbeddingProperties();
        private final KnowledgeHashing hashing = new KnowledgeHashing(new ObjectMapper());

        private Fixture() {
            properties.setDimension(3);
        }

        private KnowledgeDocumentProjection projection(Map<String, Object> payload) {
            Map<String, Object> contractPayload = new java.util.LinkedHashMap<>(payload);
            contractPayload.put("userId", 4L);
            return new KnowledgeDocumentProjection(
                    "TASK:1:PRIVATE:10", KnowledgeSourceTypeEnum.TASK, 1L, 10L,
                    null, 4L, KnowledgeVisibilityTypeEnum.PRIVATE,
                    "任务标题: 测试", "任务描述: 内容", contractPayload
            );
        }

        private KnowledgeIndexServiceImpl service() {
            return new KnowledgeIndexServiceImpl(factory, documentMapper, embeddingService,
                    vectorStoreClient, new KnowledgeChunker(), hashing,
                    new DeterministicPointIdFactory(), properties, leaseService, recovery,
                    vectorStoreManager);
        }
    }
}
