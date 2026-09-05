package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.spt.learningmanage.config.EmbeddingProperties;
import com.spt.learningmanage.constant.KnowledgeDocumentStatusEnum;
import com.spt.learningmanage.constant.KnowledgeFailureTypeEnum;
import com.spt.learningmanage.exception.KnowledgeIndexException;
import com.spt.learningmanage.mapper.AiKnowledgeDocumentMapper;
import com.spt.learningmanage.model.dto.knowledge.EmbeddingBatchResult;
import com.spt.learningmanage.model.dto.knowledge.EmbeddingCallContext;
import com.spt.learningmanage.model.dto.knowledge.VectorPayloadUpdate;
import com.spt.learningmanage.model.dto.knowledge.VectorPoint;
import com.spt.learningmanage.model.entity.AiKnowledgeDocument;
import com.spt.learningmanage.model.knowledge.IndexExecutionContext;
import com.spt.learningmanage.model.knowledge.KnowledgeChunk;
import com.spt.learningmanage.model.knowledge.KnowledgeDocumentProjection;
import com.spt.learningmanage.model.knowledge.KnowledgeSourceRef;
import com.spt.learningmanage.service.KnowledgeDocumentFactory;
import com.spt.learningmanage.service.KnowledgeEmbeddingService;
import com.spt.learningmanage.service.KnowledgeIndexService;
import com.spt.learningmanage.service.VectorStoreClient;
import com.spt.learningmanage.service.knowledge.DeterministicPointIdFactory;
import com.spt.learningmanage.service.knowledge.KnowledgeChunker;
import com.spt.learningmanage.service.knowledge.KnowledgeHashing;
import com.spt.learningmanage.service.knowledge.KnowledgeRecoveryEventService;
import com.spt.learningmanage.service.knowledge.KnowledgeSourceLeaseService;
import com.spt.learningmanage.service.knowledge.KnowledgeVectorStoreManager;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class KnowledgeIndexServiceImpl implements KnowledgeIndexService {

    private final KnowledgeDocumentFactory documentFactory;
    private final AiKnowledgeDocumentMapper documentMapper;
    private final KnowledgeEmbeddingService embeddingService;
    private final VectorStoreClient vectorStoreClient;
    private final KnowledgeTextNormalizerFacade normalizerFacade;
    private final KnowledgeChunker chunker;
    private final KnowledgeHashing hashing;
    private final DeterministicPointIdFactory pointIdFactory;
    private final EmbeddingProperties embeddingProperties;
    private final KnowledgeSourceLeaseService leaseService;
    private final KnowledgeRecoveryEventService recoveryEventService;
    private final KnowledgeVectorStoreManager vectorStoreManager;

    public KnowledgeIndexServiceImpl(KnowledgeDocumentFactory documentFactory,
                                     AiKnowledgeDocumentMapper documentMapper,
                                     KnowledgeEmbeddingService embeddingService,
                                     VectorStoreClient vectorStoreClient,
                                     KnowledgeChunker chunker,
                                     KnowledgeHashing hashing,
                                     DeterministicPointIdFactory pointIdFactory,
                                     EmbeddingProperties embeddingProperties,
                                     KnowledgeSourceLeaseService leaseService,
                                     KnowledgeRecoveryEventService recoveryEventService,
                                     KnowledgeVectorStoreManager vectorStoreManager) {
        this.documentFactory = documentFactory;
        this.documentMapper = documentMapper;
        this.embeddingService = embeddingService;
        this.vectorStoreClient = vectorStoreClient;
        this.normalizerFacade = new KnowledgeTextNormalizerFacade();
        this.chunker = chunker;
        this.hashing = hashing;
        this.pointIdFactory = pointIdFactory;
        this.embeddingProperties = embeddingProperties;
        this.leaseService = leaseService;
        this.recoveryEventService = recoveryEventService;
        this.vectorStoreManager = vectorStoreManager;
    }

    @Override
    public void reconcileSource(KnowledgeSourceRef source, IndexExecutionContext context) {
        try {
            vectorStoreManager.ensureReady();
            doReconcileSource(source, context);
        } catch (KnowledgeIndexException exception) {
            if (exception.getFailureType() == KnowledgeFailureTypeEnum.VECTOR_STORE) {
                vectorStoreManager.invalidate();
            }
            throw exception;
        }
    }

    private void doReconcileSource(KnowledgeSourceRef source, IndexExecutionContext context) {
        requireContext(context);
        leaseService.requireOwned(source, context.claimToken());
        List<KnowledgeDocumentProjection> desired = documentFactory.buildDesiredDocuments(source);
        Map<String, AiKnowledgeDocument> existing = existingByKey(source);
        Set<String> desiredKeys = new LinkedHashSet<>();

        for (KnowledgeDocumentProjection projection : desired) {
            desiredKeys.add(projection.documentKey());
            reconcileDocument(source, projection, existing.get(projection.documentKey()), context);
        }
        for (AiKnowledgeDocument obsolete : existing.values()) {
            if (!desiredKeys.contains(obsolete.getDocumentKey())
                    && !KnowledgeDocumentStatusEnum.DELETED.name().equals(obsolete.getStatus())) {
                deleteObsolete(source, obsolete, context);
            }
        }

        List<KnowledgeDocumentProjection> after = documentFactory.buildDesiredDocuments(source);
        if (!fingerprints(desired).equals(fingerprints(after))) {
            recoveryEventService.enqueue(source);
            throw new KnowledgeIndexException(KnowledgeFailureTypeEnum.STALE_SOURCE, true,
                    "索引期间来源数据发生变化", "Knowledge source changed during reconciliation", null);
        }
    }

    private void reconcileDocument(KnowledgeSourceRef source,
                                   KnowledgeDocumentProjection projection,
                                   AiKnowledgeDocument existing,
                                   IndexExecutionContext context) {
        String canonicalText = normalizerFacade.canonical(projection);
        String contentHash = hashing.contentHash(canonicalText);
        String payloadHash = hashing.payloadHash(projection.payload());
        List<KnowledgeChunk> chunks = chunker.chunk(projection.repeatPrefix(), projection.semanticBody());
        boolean forceRebuild = context.eventType() == com.spt.learningmanage.constant.KnowledgeEventTypeEnum.REBUILD;
        boolean contentChanged = forceRebuild || existing == null
                || !Objects.equals(existing.getIndexedContentHash(), contentHash)
                || !Objects.equals(existing.getChunkCount(), chunks.size())
                || !KnowledgeDocumentStatusEnum.INDEXED.name().equals(existing.getStatus());
        boolean payloadChanged = existing == null
                || !Objects.equals(existing.getIndexedPayloadHash(), payloadHash);
        int previousChunkCount = existing == null || existing.getChunkCount() == null
                ? 0 : existing.getChunkCount();
        AiKnowledgeDocument document = prepareDocument(projection, existing, contentHash, payloadHash,
                chunks.size(), context);

        if (chunks.isEmpty()) {
            leaseService.renew(source, context.claimToken());
            vectorStoreClient.deleteByDocumentKey(projection.documentKey());
            finishDocument(document, context, KnowledgeDocumentStatusEnum.SKIPPED,
                    contentHash, payloadHash, 0, "EMPTY_CONTENT", null);
            return;
        }
        LocalDateTime indexedAt = LocalDateTime.now();

        if (contentChanged) {
            List<List<Float>> vectors = embed(source, projection, chunks, context);
            leaseService.renew(source, context.claimToken());
            List<VectorPoint> points = new ArrayList<>(chunks.size());
            for (int index = 0; index < chunks.size(); index++) {
                points.add(new VectorPoint(
                        pointIdFactory.pointId(projection.documentKey(), index),
                        vectors.get(index),
                        pointPayload(projection, contentHash, payloadHash, index, indexedAt)
                ));
            }
            vectorStoreClient.upsertPoints(points);
            if (previousChunkCount > chunks.size()) {
                List<String> obsolete = new ArrayList<>();
                for (int index = chunks.size(); index < previousChunkCount; index++) {
                    obsolete.add(pointIdFactory.pointId(projection.documentKey(), index));
                }
                vectorStoreClient.deletePoints(obsolete);
            }
        } else if (payloadChanged) {
            leaseService.renew(source, context.claimToken());
            List<VectorPayloadUpdate> updates = new ArrayList<>(chunks.size());
            for (int index = 0; index < chunks.size(); index++) {
                updates.add(new VectorPayloadUpdate(
                        pointIdFactory.pointId(projection.documentKey(), index),
                        pointPayload(projection, contentHash, payloadHash, index, indexedAt)
                ));
            }
            vectorStoreClient.overwritePayload(updates);
        }

        try {
            leaseService.requireOwned(source, context.claimToken());
        } catch (KnowledgeIndexException lostLease) {
            recoveryEventService.enqueue(source);
            throw lostLease;
        }
        finishDocument(document, context, KnowledgeDocumentStatusEnum.INDEXED,
                contentHash, payloadHash, chunks.size(), null, indexedAt);
    }

    @Override
    public void markFailure(KnowledgeSourceRef source,
                            IndexExecutionContext context,
                            KnowledgeFailureTypeEnum failureType,
                            String safeError) {
        String error = safeError == null ? null
                : safeError.replace('\r', ' ').replace('\n', ' ').trim();
        if (error != null && error.length() > 1000) {
            error = error.substring(0, 1000);
        }
        documentMapper.update(null, new UpdateWrapper<AiKnowledgeDocument>()
                .eq("source_type", source.sourceType().name())
                .eq("source_id", source.sourceId())
                .eq("worker_token", context.claimToken())
                .set("status", KnowledgeDocumentStatusEnum.FAILED.name())
                .set("last_error", failureType.name() + (error == null ? "" : ": " + error)));
    }

    private List<List<Float>> embed(KnowledgeSourceRef source,
                                    KnowledgeDocumentProjection projection,
                                    List<KnowledgeChunk> chunks,
                                    IndexExecutionContext context) {
        List<List<Float>> vectors = new ArrayList<>(chunks.size());
        int batchSize = embeddingProperties.getMaxBatchSize();
        for (int start = 0; start < chunks.size(); start += batchSize) {
            leaseService.renew(source, context.claimToken());
            int end = Math.min(chunks.size(), start + batchSize);
            List<String> texts = chunks.subList(start, end).stream().map(KnowledgeChunk::text).toList();
            List<String> hashes = texts.stream().map(hashing::sha256).toList();
            EmbeddingBatchResult result = embeddingService.embedDocuments(texts,
                    new EmbeddingCallContext(projection.ownerUserId(), context.traceId(), hashes));
            if (result.vectors().size() != texts.size()) {
                throw new KnowledgeIndexException(KnowledgeFailureTypeEnum.EMBEDDING_PROTOCOL, false,
                        "Embedding 返回数量与请求不一致", "Embedding count mismatch", null);
            }
            vectors.addAll(result.vectors());
        }
        return vectors;
    }

    private AiKnowledgeDocument prepareDocument(KnowledgeDocumentProjection projection,
                                                 AiKnowledgeDocument existing,
                                                 String contentHash,
                                                 String payloadHash,
                                                 int chunkCount,
                                                 IndexExecutionContext context) {
        AiKnowledgeDocument document = existing == null ? new AiKnowledgeDocument() : existing;
        if (existing == null) {
            document.setId(IdWorker.getId());
            document.setDocumentKey(projection.documentKey());
        }
        document.setSourceType(projection.sourceType().name());
        document.setSourceId(projection.sourceId());
        document.setProjectId(projection.projectId());
        document.setTeamId(projection.teamId());
        document.setOwnerUserId(projection.ownerUserId());
        document.setVisibilityType(projection.visibilityType().name());
        document.setContentHash(contentHash);
        document.setPayloadHash(payloadHash);
        document.setNormalizerVersion(com.spt.learningmanage.service.knowledge.KnowledgeTextNormalizer.VERSION);
        document.setChunkingVersion(KnowledgeChunker.VERSION);
        document.setEmbeddingModel(embeddingProperties.getModel());
        document.setEmbeddingDimension(embeddingProperties.getDimension());
        document.setChunkCount(chunkCount);
        document.setStatus(KnowledgeDocumentStatusEnum.INDEXING.name());
        document.setWorkerToken(context.claimToken());
        document.setLastEventId(context.eventId());
        document.setSkipReason(null);
        document.setLastError(null);
        if (existing == null) {
            if (documentMapper.insert(document) != 1) {
                throw internal("Unable to insert knowledge document");
            }
        } else if (documentMapper.updateById(document) != 1) {
            throw internal("Unable to prepare knowledge document");
        }
        return document;
    }

    private void finishDocument(AiKnowledgeDocument document,
                                IndexExecutionContext context,
                                KnowledgeDocumentStatusEnum status,
                                String contentHash,
                                String payloadHash,
                                int chunkCount,
                                String skipReason,
                                LocalDateTime indexedAt) {
        int rows = documentMapper.update(null, new UpdateWrapper<AiKnowledgeDocument>()
                .eq("id", document.getId())
                .eq("worker_token", context.claimToken())
                .set("status", status.name())
                .set("content_hash", contentHash)
                .set("payload_hash", payloadHash)
                .set("indexed_content_hash", contentHash)
                .set("indexed_payload_hash", payloadHash)
                .set("chunk_count", chunkCount)
                .set("skip_reason", skipReason)
                .set("indexed_at", indexedAt)
                .set("last_error", null));
        if (rows != 1) {
            throw new KnowledgeIndexException(KnowledgeFailureTypeEnum.STALE_SOURCE, true,
                    "知识文档写入令牌已失效", "Knowledge document fencing token was lost", null);
        }
    }

    private void deleteObsolete(KnowledgeSourceRef source,
                                AiKnowledgeDocument document,
                                IndexExecutionContext context) {
        leaseService.renew(source, context.claimToken());
        documentMapper.update(null, new UpdateWrapper<AiKnowledgeDocument>()
                .eq("id", document.getId())
                .set("worker_token", context.claimToken())
                .set("status", KnowledgeDocumentStatusEnum.INDEXING.name())
                .set("last_event_id", context.eventId()));
        vectorStoreClient.deleteByDocumentKey(document.getDocumentKey());
        int rows = documentMapper.update(null, new UpdateWrapper<AiKnowledgeDocument>()
                .eq("id", document.getId())
                .eq("worker_token", context.claimToken())
                .set("status", KnowledgeDocumentStatusEnum.DELETED.name())
                .set("chunk_count", 0)
                .set("indexed_content_hash", null)
                .set("indexed_payload_hash", null)
                .set("indexed_at", null));
        if (rows != 1) {
            recoveryEventService.enqueue(source);
            throw new KnowledgeIndexException(KnowledgeFailureTypeEnum.STALE_SOURCE, true,
                    "知识文档删除令牌已失效", "Knowledge document delete fencing token was lost", null);
        }
    }

    private Map<String, AiKnowledgeDocument> existingByKey(KnowledgeSourceRef source) {
        Map<String, AiKnowledgeDocument> result = new HashMap<>();
        for (AiKnowledgeDocument document : documentMapper.selectBySource(
                source.sourceType().name(), source.sourceId())) {
            result.put(document.getDocumentKey(), document);
        }
        return result;
    }

    private Map<String, String> fingerprints(List<KnowledgeDocumentProjection> projections) {
        Map<String, String> result = new LinkedHashMap<>();
        for (KnowledgeDocumentProjection projection : projections) {
            result.put(projection.documentKey(), hashing.contentHash(normalizerFacade.canonical(projection))
                    + ':' + hashing.payloadHash(projection.payload()));
        }
        return result;
    }

    private Map<String, Object> pointPayload(KnowledgeDocumentProjection projection,
                                             String contentHash,
                                             String payloadHash,
                                             int chunkIndex,
                                             LocalDateTime indexedAt) {
        Map<String, Object> payload = new LinkedHashMap<>(projection.payload());
        payload.put("documentKey", projection.documentKey());
        payload.put("chunkIndex", chunkIndex);
        payload.put("contentHash", contentHash);
        payload.put("payloadHash", payloadHash);
        payload.put("indexedAt", indexedAt.toString());
        return payload;
    }

    private void requireContext(IndexExecutionContext context) {
        if (context == null || context.eventId() == null || context.claimToken() == null
                || context.claimToken().isBlank()) {
            throw new IllegalArgumentException("Index execution context is invalid");
        }
    }

    private KnowledgeIndexException internal(String message) {
        return new KnowledgeIndexException(KnowledgeFailureTypeEnum.INTERNAL, true,
                "知识文档状态写入失败", message, null);
    }

    /** Keeps canonical-text composition explicit and independent from mapper entities. */
    private static final class KnowledgeTextNormalizerFacade {
        private String canonical(KnowledgeDocumentProjection projection) {
            return projection.canonicalText();
        }
    }
}
