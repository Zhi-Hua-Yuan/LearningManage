package com.spt.learningmanage.service.impl;

import com.spt.learningmanage.constant.CleanupResourceTypeEnum;
import com.spt.learningmanage.constant.CleanupRunStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiDataCleanupItemMapper;
import com.spt.learningmanage.model.entity.AiDataCleanupItem;
import com.spt.learningmanage.model.entity.AiDataCleanupRun;
import com.spt.learningmanage.model.ops.CleanupBatchResult;
import com.spt.learningmanage.service.DataCleanupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class CleanupBatchTransactionService {
    private final DataCleanupService cleanupService;
    private final AiDataCleanupItemMapper itemMapper;

    public CleanupBatchTransactionService(DataCleanupService cleanupService,
                                           AiDataCleanupItemMapper itemMapper) {
        this.cleanupService = cleanupService;
        this.itemMapper = itemMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public CleanupBatchResult process(AiDataCleanupRun run,
                                      AiDataCleanupItem item,
                                      CleanupResourceTypeEnum type,
                                      int batchSize) {
        CleanupBatchResult batch = cleanupService.processBatch(
                type, item.getCutoffTime(), value(item.getCursorId()), batchSize);
        long cursor = batch.nextCursor() > 0 ? batch.nextCursor() : value(item.getCursorId());
        long scanned = value(item.getScannedCount()) + batch.scanned();
        long redacted = value(item.getRedactedCount()) + batch.redacted();
        long deleted = value(item.getDeletedCount()) + batch.deleted();
        String status = batch.finished()
                ? CleanupRunStatusEnum.SUCCEEDED.name() : CleanupRunStatusEnum.RUNNING.name();
        LocalDateTime finishedAt = batch.finished() ? LocalDateTime.now() : null;
        if (itemMapper.updateProgressFenced(item.getId(), run.getId(), run.getExecutionToken(),
                cursor, scanned, redacted, deleted, status, finishedAt) != 1) {
            throw new BusinessException(ErrorCode.CLEANUP_ALREADY_RUNNING, "清理任务租约已失效");
        }
        item.setCursorId(cursor);
        item.setScannedCount(scanned);
        item.setRedactedCount(redacted);
        item.setDeletedCount(deleted);
        item.setStatus(status);
        item.setFinishedAt(finishedAt);
        return batch;
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }
}
