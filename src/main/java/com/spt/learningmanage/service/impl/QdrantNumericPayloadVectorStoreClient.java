package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.model.dto.knowledge.VectorDocumentSnapshot;
import com.spt.learningmanage.model.dto.knowledge.VectorPayloadUpdate;
import com.spt.learningmanage.model.dto.knowledge.VectorPoint;
import com.spt.learningmanage.service.VectorStoreClient;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provider-protocol adapter that keeps long identifiers numeric in Qdrant.
 *
 * <p>The application's HTTP ObjectMapper intentionally renders Java longs as
 * strings for browser precision. Qdrant integer payload indexes require JSON
 * numbers, so this adapter converts long map values to BigInteger immediately
 * before delegating to the sealed Stage 4 client. Business payloads and public
 * API serialization remain unchanged.</p>
 */
@Primary
@Service
public class QdrantNumericPayloadVectorStoreClient implements VectorStoreClient {
    private static final Set<String> INTEGER_INDEX_FIELDS = Set.of(
            "sourceId", "projectId", "userId", "ownerUserId", "teamId");
    private final QdrantVectorStoreClient delegate;

    public QdrantNumericPayloadVectorStoreClient(QdrantVectorStoreClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public void ensureCollection() {
        delegate.ensureCollection();
    }

    @Override
    public void upsertPoints(List<VectorPoint> points) {
        if (points == null) {
            delegate.upsertPoints(null);
            return;
        }
        delegate.upsertPoints(points.stream()
                .map(point -> new VectorPoint(point.id(), point.vector(), numericPayload(point.payload())))
                .toList());
    }

    @Override
    public void overwritePayload(List<VectorPayloadUpdate> updates) {
        if (updates == null) {
            delegate.overwritePayload(null);
            return;
        }
        delegate.overwritePayload(updates.stream()
                .map(update -> new VectorPayloadUpdate(update.pointId(), numericPayload(update.payload())))
                .toList());
    }

    @Override
    public void deletePoints(List<String> pointIds) {
        delegate.deletePoints(pointIds);
    }

    @Override
    public void deleteByDocumentKey(String documentKey) {
        delegate.deleteByDocumentKey(documentKey);
    }

    @Override
    public VectorDocumentSnapshot inspectByDocumentKey(String documentKey) {
        return delegate.inspectByDocumentKey(documentKey);
    }

    private Map<String, Object> numericPayload(Map<String, Object> payload) {
        Map<String, Object> result = new LinkedHashMap<>();
        payload.forEach((key, value) -> result.put(key, numericValue(key, value)));
        return result;
    }

    private Object numericValue(String key, Object value) {
        if (!INTEGER_INDEX_FIELDS.contains(key) || value == null) {
            return value;
        }
        if (value instanceof BigInteger) {
            return value;
        }
        if (value instanceof Number number) {
            return BigInteger.valueOf(number.longValue());
        }
        try {
            return new BigInteger(value.toString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Qdrant integer payload field is invalid: " + key, exception);
        }
    }
}
