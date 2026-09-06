package com.spt.learningmanage.service.rag;

import com.spt.learningmanage.constant.KnowledgeEventTypeEnum;
import com.spt.learningmanage.constant.KnowledgeSourceTypeEnum;
import com.spt.learningmanage.constant.KnowledgeVisibilityTypeEnum;
import com.spt.learningmanage.model.dto.knowledge.VectorSearchHit;
import com.spt.learningmanage.model.knowledge.KnowledgeChunk;
import com.spt.learningmanage.model.knowledge.KnowledgeDocumentProjection;
import com.spt.learningmanage.model.knowledge.KnowledgeSourceRef;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.model.rag.RagCandidate;
import com.spt.learningmanage.service.KnowledgeDocumentFactory;
import com.spt.learningmanage.service.KnowledgeIndexEventPublisher;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.service.knowledge.KnowledgeChunker;
import com.spt.learningmanage.service.knowledge.KnowledgeHashing;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RagCandidateHydrator {
    private final KnowledgeDocumentFactory documentFactory;
    private final KnowledgeChunker chunker;
    private final KnowledgeHashing hashing;
    private final KnowledgeIndexEventPublisher eventPublisher;
    private final PermissionService permissionService;

    public RagCandidateHydrator(KnowledgeDocumentFactory documentFactory,
                                KnowledgeChunker chunker,
                                KnowledgeHashing hashing,
                                KnowledgeIndexEventPublisher eventPublisher,
                                PermissionService permissionService) {
        this.documentFactory = documentFactory;
        this.chunker = chunker;
        this.hashing = hashing;
        this.eventPublisher = eventPublisher;
        this.permissionService = permissionService;
    }

    public List<RagCandidate> hydrate(Long actorUserId,
                                      ProjectAccessScope scope,
                                      List<VectorSearchHit> hits) {
        Map<KnowledgeSourceRef, List<KnowledgeDocumentProjection>> projections = new LinkedHashMap<>();
        List<RagCandidate> result = new ArrayList<>();
        List<ParsedHit> parsedHits = new ArrayList<>();
        for (VectorSearchHit hit : hits) {
            ParsedPayload payload;
            try {
                payload = parse(hit.payload());
            } catch (RuntimeException ignoredMalformedCandidate) {
                // Qdrant payload is untrusted. Malformed candidates are discarded without
                // allowing their values to reach SQL, logs, rerank, or the chat prompt.
                continue;
            }
            if (!scope.projectId().equals(payload.projectId()) || !visible(actorUserId, scope, payload)) {
                continue;
            }
            parsedHits.add(new ParsedHit(hit, payload));
        }
        Set<Long> readableTasks = permissionService.filterReadableTaskIds(actorUserId,
                parsedHits.stream().map(ParsedHit::payload)
                        .filter(value -> value.sourceType() == KnowledgeSourceTypeEnum.TASK)
                        .map(ParsedPayload::sourceId).distinct().toList());
        Set<Long> readableReviews = permissionService.filterReadableWeeklyReviewIds(actorUserId,
                parsedHits.stream().map(ParsedHit::payload)
                        .filter(value -> value.sourceType() == KnowledgeSourceTypeEnum.WEEKLY_REVIEW)
                        .map(ParsedPayload::sourceId).distinct().toList());

        for (ParsedHit parsed : parsedHits) {
            VectorSearchHit hit = parsed.hit();
            ParsedPayload payload = parsed.payload();
            if (payload.sourceType() == KnowledgeSourceTypeEnum.TASK
                    && !readableTasks.contains(payload.sourceId())) {
                continue;
            }
            if (payload.sourceType() == KnowledgeSourceTypeEnum.WEEKLY_REVIEW
                    && !readableReviews.contains(payload.sourceId())) {
                continue;
            }
            KnowledgeSourceRef source = new KnowledgeSourceRef(payload.sourceType(), payload.sourceId());
            List<KnowledgeDocumentProjection> desired = projections.computeIfAbsent(source,
                    documentFactory::buildDesiredDocuments);
            KnowledgeDocumentProjection projection = desired.stream()
                    .filter(value -> value.documentKey().equals(payload.documentKey()))
                    .findFirst().orElse(null);
            if (projection == null || !projection.projectId().equals(scope.projectId())
                    || !visible(actorUserId, scope, projection)) {
                scheduleCorrection(source);
                continue;
            }
            String contentHash = hashing.contentHash(projection.canonicalText());
            String payloadHash = hashing.payloadHash(projection.payload());
            if (!payload.sourceVersion().equals(contentHash + ':' + payloadHash)) {
                scheduleCorrection(source);
                continue;
            }
            List<KnowledgeChunk> chunks = chunker.chunk(projection.repeatPrefix(), projection.semanticBody());
            if (payload.chunkIndex() < 0 || payload.chunkIndex() >= chunks.size()) {
                scheduleCorrection(source);
                continue;
            }
            KnowledgeChunk chunk = chunks.get(payload.chunkIndex());
            result.add(new RagCandidate(
                    hit.pointId(), hit.pointId(), projection.documentKey(), projection.sourceType(),
                    projection.sourceId(), payload.chunkIndex(), title(projection), chunk.text(),
                    contentHash, payloadHash, hit.score(), null, payload.updatedAt()));
        }
        return List.copyOf(result);
    }

    private boolean visible(Long actorUserId, ProjectAccessScope scope, ParsedPayload payload) {
        if ("PRIVATE".equals(payload.visibilityType())) {
            return actorUserId.equals(payload.ownerUserId());
        }
        return "TEAM".equals(payload.visibilityType()) && scope.teamId() != null
                && scope.teamId().equals(payload.teamId());
    }

    private boolean visible(Long actorUserId,
                            ProjectAccessScope scope,
                            KnowledgeDocumentProjection projection) {
        if (projection.visibilityType() == KnowledgeVisibilityTypeEnum.PRIVATE) {
            return actorUserId.equals(projection.ownerUserId());
        }
        return projection.visibilityType() == KnowledgeVisibilityTypeEnum.TEAM
                && scope.teamId() != null && scope.teamId().equals(projection.teamId());
    }

    private ParsedPayload parse(Map<String, Object> payload) {
        String sourceType = required(payload, "sourceType").toUpperCase(Locale.ROOT);
        return new ParsedPayload(
                KnowledgeSourceTypeEnum.valueOf(sourceType),
                positiveLong(payload, "sourceId"),
                positiveLong(payload, "projectId"),
                nullableLong(payload, "teamId"),
                positiveLong(payload, "ownerUserId"),
                required(payload, "visibilityType").toUpperCase(Locale.ROOT),
                required(payload, "documentKey"),
                nonNegativeInt(payload, "chunkIndex"),
                required(payload, "sourceVersion"),
                parseDateTime(payload.get("updatedAt"))
        );
    }

    private String required(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("missing payload field " + key);
        }
        return value.toString().trim();
    }

    private Long positiveLong(Map<String, Object> payload, String key) {
        Long value = nullableLong(payload, key);
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("invalid payload field " + key);
        }
        return value;
    }

    private Long nullableLong(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return Long.valueOf(value.toString());
    }

    private int nonNegativeInt(Map<String, Object> payload, String key) {
        int value = Integer.parseInt(required(payload, key));
        if (value < 0) {
            throw new IllegalArgumentException("invalid payload field " + key);
        }
        return value;
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.toString()).toLocalDateTime();
        } catch (RuntimeException ignored) {
            return LocalDateTime.parse(value.toString());
        }
    }

    private String title(KnowledgeDocumentProjection projection) {
        String value = projection.repeatPrefix() == null ? "" : projection.repeatPrefix().trim();
        if (projection.sourceType() == KnowledgeSourceTypeEnum.TASK && value.startsWith("任务标题:")) {
            return value.substring("任务标题:".length()).trim();
        }
        return value.isBlank() ? projection.sourceType().name() + "#" + projection.sourceId() : value;
    }

    private void scheduleCorrection(KnowledgeSourceRef source) {
        try {
            eventPublisher.publish(source.sourceType(), source.sourceId(), KnowledgeEventTypeEnum.SOURCE_CHANGED);
        } catch (RuntimeException ignored) {
            // Retrieval correctness does not depend on the corrective event succeeding.
        }
    }

    private record ParsedPayload(
            KnowledgeSourceTypeEnum sourceType,
            Long sourceId,
            Long projectId,
            Long teamId,
            Long ownerUserId,
            String visibilityType,
            String documentKey,
            int chunkIndex,
            String sourceVersion,
            LocalDateTime updatedAt
    ) {
    }

    private record ParsedHit(VectorSearchHit hit, ParsedPayload payload) {
    }
}
