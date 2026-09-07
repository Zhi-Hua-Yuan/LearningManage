package com.spt.learningmanage.service;

import com.spt.learningmanage.constant.CleanupResourceTypeEnum;
import com.spt.learningmanage.model.ops.CleanupBatchResult;

import java.time.LocalDateTime;

public interface DataCleanupService {
    long estimate(CleanupResourceTypeEnum type, LocalDateTime cutoff);

    CleanupBatchResult processBatch(CleanupResourceTypeEnum type,
                                    LocalDateTime cutoff,
                                    long cursor,
                                    int batchSize);
}
