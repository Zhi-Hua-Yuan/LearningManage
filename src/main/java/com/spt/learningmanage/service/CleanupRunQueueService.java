package com.spt.learningmanage.service;

import com.spt.learningmanage.model.entity.AiDataCleanupRun;

public interface CleanupRunQueueService {
    AiDataCleanupRun claimOne(String workerId);

    boolean heartbeat(AiDataCleanupRun run);

    boolean complete(AiDataCleanupRun run, String status, long scanned,
                     long estimated, long affected, long failures, String error);

    boolean releaseForResume(AiDataCleanupRun run);
}
