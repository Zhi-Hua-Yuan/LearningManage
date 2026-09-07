package com.spt.learningmanage.service;

import com.spt.learningmanage.model.entity.AiCallLog;
import com.spt.learningmanage.model.ops.CleanupBatchResult;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/** Internal metadata/retention boundary; never returns request or response bodies. */
public interface AiCallLogOperationsService {
    AiCallLog latestMetadata();

    BigDecimal sumEstimatedCost(LocalDateTime from, LocalDateTime to);

    long countBodyCleanupCandidates(LocalDateTime cutoff);

    long countMetadataCleanupCandidates(LocalDateTime cutoff);

    CleanupBatchResult purgeBodyBatch(LocalDateTime cutoff, long cursor, int batchSize);

    CleanupBatchResult deleteMetadataBatch(LocalDateTime cutoff, long cursor, int batchSize);
}
