package com.spt.learningmanage.service.rag;

import com.spt.learningmanage.constant.KnowledgeVisibilityTypeEnum;
import com.spt.learningmanage.constant.RagSourceValidationStatus;
import com.spt.learningmanage.model.entity.AiRagResultSource;
import com.spt.learningmanage.model.knowledge.KnowledgeChunk;
import com.spt.learningmanage.model.knowledge.KnowledgeDocumentProjection;
import com.spt.learningmanage.model.knowledge.KnowledgeSourceRef;
import com.spt.learningmanage.model.permission.ProjectAccessScope;
import com.spt.learningmanage.model.rag.RagCandidate;
import com.spt.learningmanage.service.KnowledgeDocumentFactory;
import com.spt.learningmanage.service.knowledge.KnowledgeChunker;
import com.spt.learningmanage.service.knowledge.KnowledgeHashing;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagSourceVerifier {
    private final KnowledgeDocumentFactory documentFactory;
    private final KnowledgeChunker chunker;
    private final KnowledgeHashing hashing;

    public RagSourceVerifier(KnowledgeDocumentFactory documentFactory,
                             KnowledgeChunker chunker,
                             KnowledgeHashing hashing) {
        this.documentFactory = documentFactory;
        this.chunker = chunker;
        this.hashing = hashing;
    }

    public RagSourceValidationStatus verifyCandidates(Long actorUserId,
                                                       ProjectAccessScope scope,
                                                       List<RagCandidate> candidates) {
        Map<KnowledgeSourceRef, List<KnowledgeDocumentProjection>> cache = new HashMap<>();
        for (RagCandidate candidate : candidates) {
            KnowledgeSourceRef ref = new KnowledgeSourceRef(candidate.sourceType(), candidate.sourceId());
            KnowledgeDocumentProjection projection = projection(cache, ref, candidate.documentKey());
            RagSourceValidationStatus status = validate(actorUserId, scope, projection,
                    candidate.chunkIndex(), candidate.contentHash(), candidate.payloadHash(), candidate.text());
            if (status != RagSourceValidationStatus.VALID) {
                return status;
            }
        }
        return RagSourceValidationStatus.VALID;
    }

    public RagSourceValidationStatus verifyStored(Long actorUserId,
                                                   ProjectAccessScope scope,
                                                   List<AiRagResultSource> sources) {
        Map<KnowledgeSourceRef, List<KnowledgeDocumentProjection>> cache = new HashMap<>();
        RagSourceValidationStatus aggregate = RagSourceValidationStatus.VALID;
        for (AiRagResultSource source : sources) {
            KnowledgeSourceRef ref;
            try {
                ref = new KnowledgeSourceRef(
                        com.spt.learningmanage.constant.KnowledgeSourceTypeEnum.valueOf(source.getSourceType()),
                        source.getSourceId());
            } catch (RuntimeException exception) {
                return RagSourceValidationStatus.INVALIDATED;
            }
            KnowledgeDocumentProjection projection = projection(cache, ref, source.getDocumentKey());
            RagSourceValidationStatus status = validate(actorUserId, scope, projection,
                    source.getChunkIndex(), source.getContentHash(), source.getPayloadHash(), null);
            if (status == RagSourceValidationStatus.INVALIDATED) {
                return status;
            }
            if (status == RagSourceValidationStatus.STALE) {
                aggregate = status;
            }
        }
        return aggregate;
    }

    private KnowledgeDocumentProjection projection(
            Map<KnowledgeSourceRef, List<KnowledgeDocumentProjection>> cache,
            KnowledgeSourceRef ref,
            String documentKey) {
        return cache.computeIfAbsent(ref, documentFactory::buildDesiredDocuments).stream()
                .filter(value -> value.documentKey().equals(documentKey))
                .findFirst().orElse(null);
    }

    private RagSourceValidationStatus validate(Long actorUserId,
                                                ProjectAccessScope scope,
                                                KnowledgeDocumentProjection projection,
                                                Integer chunkIndex,
                                                String expectedContentHash,
                                                String expectedPayloadHash,
                                                String expectedText) {
        if (projection == null || !scope.projectId().equals(projection.projectId())
                || !visible(actorUserId, scope, projection)) {
            return RagSourceValidationStatus.INVALIDATED;
        }
        String contentHash = hashing.contentHash(projection.canonicalText());
        String payloadHash = hashing.payloadHash(projection.payload());
        if (!contentHash.equals(expectedContentHash) || !payloadHash.equals(expectedPayloadHash)) {
            return RagSourceValidationStatus.STALE;
        }
        List<KnowledgeChunk> chunks = chunker.chunk(projection.repeatPrefix(), projection.semanticBody());
        if (chunkIndex == null || chunkIndex < 0 || chunkIndex >= chunks.size()) {
            return RagSourceValidationStatus.INVALIDATED;
        }
        if (expectedText != null && !expectedText.equals(chunks.get(chunkIndex).text())) {
            return RagSourceValidationStatus.STALE;
        }
        return RagSourceValidationStatus.VALID;
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
}
