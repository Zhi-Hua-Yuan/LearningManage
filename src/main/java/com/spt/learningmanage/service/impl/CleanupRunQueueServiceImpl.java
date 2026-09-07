package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.config.DataCleanupProperties;
import com.spt.learningmanage.mapper.AiDataCleanupRunMapper;
import com.spt.learningmanage.model.entity.AiDataCleanupRun;
import com.spt.learningmanage.service.CleanupRunQueueService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CleanupRunQueueServiceImpl implements CleanupRunQueueService {
    private final AiDataCleanupRunMapper mapper;
    private final DataCleanupProperties properties;

    public CleanupRunQueueServiceImpl(AiDataCleanupRunMapper mapper, DataCleanupProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiDataCleanupRun claimOne(String workerId) {
        LocalDateTime now = LocalDateTime.now();
        AiDataCleanupRun run = mapper.selectClaimableForUpdate(now);
        if (run == null) {
            return null;
        }
        String token = UUID.randomUUID().toString();
        if (mapper.claim(run.getId(), workerId, token, now,
                now.plusSeconds(properties.getLeaseSeconds())) != 1) {
            return null;
        }
        run.setStatus("RUNNING");
        run.setWorkerId(workerId);
        run.setExecutionToken(token);
        run.setLeaseUntil(now.plusSeconds(properties.getLeaseSeconds()));
        return run;
    }

    @Override
    public boolean heartbeat(AiDataCleanupRun run) {
        LocalDateTime now = LocalDateTime.now();
        return mapper.heartbeat(run.getId(), run.getExecutionToken(), now,
                now.plusSeconds(properties.getLeaseSeconds())) == 1;
    }

    @Override
    public boolean complete(AiDataCleanupRun run, String status, long scanned,
                            long estimated, long affected, long failures, String error) {
        return mapper.complete(run.getId(), run.getExecutionToken(), status, scanned,
                estimated, affected, failures, error, LocalDateTime.now()) == 1;
    }

    @Override
    public boolean releaseForResume(AiDataCleanupRun run) {
        return mapper.releaseForResume(run.getId(), run.getExecutionToken()) == 1;
    }
}
