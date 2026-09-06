package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.model.dto.knowledge.VectorPayloadUpdate;
import com.spt.learningmanage.model.dto.knowledge.VectorPoint;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class QdrantNumericPayloadVectorStoreClientTest {

    @Test
    void convertsLongPayloadValuesToJsonNumericCompatibleValuesBeforeUpsert() {
        QdrantVectorStoreClient delegate = mock(QdrantVectorStoreClient.class);
        var client = new QdrantNumericPayloadVectorStoreClient(delegate);

        client.upsertPoints(List.of(new VectorPoint("p1", List.of(0.1f), Map.of(
                "projectId", 9_950_002L,
                "ownerUserId", 9_950_001L,
                "visibilityType", "PRIVATE"))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<VectorPoint>> captor = ArgumentCaptor.forClass(List.class);
        verify(delegate).upsertPoints(captor.capture());
        Map<String, Object> payload = captor.getValue().get(0).payload();
        assertEquals(BigInteger.valueOf(9_950_002L), payload.get("projectId"));
        assertEquals(BigInteger.valueOf(9_950_001L), payload.get("ownerUserId"));
        assertEquals("PRIVATE", payload.get("visibilityType"));
    }

    @Test
    void convertsLongPayloadValuesBeforePayloadOverwrite() {
        QdrantVectorStoreClient delegate = mock(QdrantVectorStoreClient.class);
        var client = new QdrantNumericPayloadVectorStoreClient(delegate);

        client.overwritePayload(List.of(new VectorPayloadUpdate("p1", Map.of("teamId", "88"))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<VectorPayloadUpdate>> captor = ArgumentCaptor.forClass(List.class);
        verify(delegate).overwritePayload(captor.capture());
        assertEquals(BigInteger.valueOf(88L), captor.getValue().get(0).payload().get("teamId"));
    }
}
