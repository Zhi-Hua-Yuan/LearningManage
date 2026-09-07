package com.spt.learningmanage.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spt.learningmanage.config.DataCleanupProperties;
import com.spt.learningmanage.constant.CleanupResourceTypeEnum;
import com.spt.learningmanage.constant.CleanupRunStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiDataCleanupItemMapper;
import com.spt.learningmanage.mapper.AiDataCleanupRunMapper;
import com.spt.learningmanage.model.entity.AiDataCleanupItem;
import com.spt.learningmanage.model.entity.AiDataCleanupRun;
import com.spt.learningmanage.model.ops.CleanupBatchResult;
import com.spt.learningmanage.observability.AiMetricsRecorder;
import com.spt.learningmanage.service.CleanupRunQueueService;
import com.spt.learningmanage.service.DataCleanupService;
import com.spt.learningmanage.service.CleanupRunService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class DataCleanupWorker {
    private final AiDataCleanupRunMapper runMapper;
    private final AiDataCleanupItemMapper itemMapper;
    private final DataCleanupService cleanupService;
    private final CleanupRunQueueService queueService;
    private final DataCleanupProperties properties;
    private final AiMetricsRecorder metrics;
    private final CleanupRunService runService;

    public DataCleanupWorker(AiDataCleanupRunMapper runMapper,
                             AiDataCleanupItemMapper itemMapper,
                             DataCleanupService cleanupService,
                             CleanupRunQueueService queueService,
                             DataCleanupProperties properties,
                             AiMetricsRecorder metrics,
                             CleanupRunService runService) {
        this.runMapper = runMapper;
        this.itemMapper = itemMapper;
        this.cleanupService = cleanupService;
        this.queueService = queueService;
        this.properties = properties;
        this.metrics = metrics;
        this.runService = runService;
    }

    public void process(AiDataCleanupRun run) {
        long startedAt = System.currentTimeMillis();
        long scanned = 0;
        long estimated = 0;
        long affected = 0;
        long failures = 0;
        AiDataCleanupItem currentItem = null;
        try {
            List<AiDataCleanupItem> items = items(run.getRunId());
            if (run.getDryRun() == 0 && !hasProgress(items)) {
                verifyEstimate(run, items);
            }
            for (AiDataCleanupItem item : items) {
                currentItem = item;
                if (!queueService.heartbeat(run)) {
                    return;
                }
                if (CleanupRunStatusEnum.SUCCEEDED.name().equals(item.getStatus())) {
                    scanned += value(item.getScannedCount());
                    estimated += value(item.getEstimatedCount());
                    affected += value(item.getRedactedCount()) + value(item.getDeletedCount());
                    continue;
                }
                if (cancelRequested(run)) {
                    cancelRemaining(run.getRunId());
                    queueService.complete(run, CleanupRunStatusEnum.CANCELED.name(),
                            scanned, estimated, affected, failures, null);
                    metrics.recordCleanup("CANCELED", elapsed(startedAt), affected);
                    return;
                }
                markItemRunning(item);
                CleanupResourceTypeEnum type = CleanupResourceTypeEnum.valueOf(item.getResourceType());
                if (run.getDryRun() == 0) {
                    scanned += value(item.getScannedCount());
                    affected += value(item.getRedactedCount()) + value(item.getDeletedCount());
                }
                long itemEstimate = value(item.getEstimatedCount()) > 0
                        ? value(item.getEstimatedCount())
                        : cleanupService.estimate(type, item.getCutoffTime());
                item.setEstimatedCount(itemEstimate);
                estimated += itemEstimate;
                if (run.getDryRun() == 1) {
                    if (!queueService.heartbeat(run)) {
                        return;
                    }
                    item.setScannedCount(itemEstimate);
                    item.setStatus(CleanupRunStatusEnum.SUCCEEDED.name());
                    item.setFinishedAt(LocalDateTime.now());
                    itemMapper.updateById(item);
                    scanned += itemEstimate;
                    continue;
                }
                while (true) {
                    if (timedOut(startedAt)) {
                        persistItem(item);
                        queueService.releaseForResume(run);
                        return;
                    }
                    if (cancelRequested(run)) {
                        cancelRemaining(run.getRunId());
                        queueService.complete(run, CleanupRunStatusEnum.CANCELED.name(),
                                scanned, estimated, affected, failures, null);
                        metrics.recordCleanup("CANCELED", elapsed(startedAt), affected);
                        return;
                    }
                    if (!queueService.heartbeat(run)) {
                        return;
                    }
                    CleanupBatchResult batch = cleanupService.processBatch(type, item.getCutoffTime(),
                            value(item.getCursorId()), properties.getBatchSize());
                    item.setScannedCount(value(item.getScannedCount()) + batch.scanned());
                    item.setRedactedCount(value(item.getRedactedCount()) + batch.redacted());
                    item.setDeletedCount(value(item.getDeletedCount()) + batch.deleted());
                    if (batch.nextCursor() > 0) {
                        item.setCursorId(batch.nextCursor());
                    }
                    scanned += batch.scanned();
                    affected += batch.affected();
                    if (!queueService.heartbeat(run)) {
                        return;
                    }
                    persistItem(item);
                    if (batch.finished()) {
                        item.setStatus(CleanupRunStatusEnum.SUCCEEDED.name());
                        item.setFinishedAt(LocalDateTime.now());
                        itemMapper.updateById(item);
                        break;
                    }
                }
            }
            if (!queueService.complete(run, CleanupRunStatusEnum.SUCCEEDED.name(),
                    scanned, estimated, affected, failures, null)) {
                throw new BusinessException(ErrorCode.CLEANUP_ALREADY_RUNNING,
                        "清理任务终态写入冲突");
            }
            metrics.recordCleanup("SUCCEEDED", elapsed(startedAt), affected);
            if (run.getDryRun() == 1 && "SCHEDULED".equals(run.getTriggerType())) {
                runService.submitScheduledFormal(run.getRunId());
            }
        } catch (RuntimeException exception) {
            failures++;
            String safeError = safe(exception);
            if (currentItem != null && CleanupRunStatusEnum.RUNNING.name().equals(currentItem.getStatus())) {
                currentItem.setStatus(CleanupRunStatusEnum.FAILED.name());
                currentItem.setErrorSummary(safeError);
                currentItem.setFinishedAt(LocalDateTime.now());
                itemMapper.updateById(currentItem);
            }
            String status = affected > 0 ? CleanupRunStatusEnum.PARTIAL.name()
                    : CleanupRunStatusEnum.FAILED.name();
            queueService.complete(run, status, scanned, estimated, affected, failures, safeError);
            metrics.recordCleanup(status, elapsed(startedAt), affected);
        }
    }

    private void verifyEstimate(AiDataCleanupRun run, List<AiDataCleanupItem> items) {
        if (run.getApprovedDryRunId() == null) {
            throw new BusinessException(ErrorCode.CLEANUP_DRY_RUN_REQUIRED);
        }
        AiDataCleanupRun approved = runMapper.selectById(run.getApprovedDryRunId());
        if (approved == null || approved.getDryRun() != 1
                || !CleanupRunStatusEnum.SUCCEEDED.name().equals(approved.getStatus())
                || !run.getPolicyVersion().equals(approved.getPolicyVersion())
                || !run.getResourceHash().equals(approved.getResourceHash())) {
            throw new BusinessException(ErrorCode.CLEANUP_DRY_RUN_REQUIRED);
        }
        List<AiDataCleanupItem> approvedItems = items(approved.getRunId());
        for (AiDataCleanupItem item : items) {
            AiDataCleanupItem preview = approvedItems.stream()
                    .filter(candidate -> candidate.getResourceType().equals(item.getResourceType()))
                    .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.CLEANUP_DRY_RUN_REQUIRED));
            long current = cleanupService.estimate(
                    CleanupResourceTypeEnum.valueOf(item.getResourceType()), item.getCutoffTime());
            long expected = value(preview.getEstimatedCount());
            long tolerance = Math.max(properties.getEstimateDriftMinRows(),
                    Math.round(expected * properties.getEstimateDriftRatio()));
            if (Math.abs(current - expected) > tolerance) {
                throw new BusinessException(ErrorCode.CLEANUP_ESTIMATE_CHANGED);
            }
        }
    }

    private List<AiDataCleanupItem> items(String runId) {
        return itemMapper.selectList(new LambdaQueryWrapper<AiDataCleanupItem>()
                .eq(AiDataCleanupItem::getRunId, runId).orderByAsc(AiDataCleanupItem::getId));
    }

    private boolean hasProgress(List<AiDataCleanupItem> items) {
        return items.stream().anyMatch(item -> value(item.getCursorId()) > 0
                || value(item.getScannedCount()) > 0
                || value(item.getRedactedCount()) > 0
                || value(item.getDeletedCount()) > 0);
    }

    private boolean cancelRequested(AiDataCleanupRun run) {
        AiDataCleanupRun current = runMapper.selectById(run.getId());
        return current == null || current.getCancelRequestedAt() != null;
    }

    private void markItemRunning(AiDataCleanupItem item) {
        item.setStatus("RUNNING");
        if (item.getStartedAt() == null) {
            item.setStartedAt(LocalDateTime.now());
        }
        item.setErrorSummary(null);
        itemMapper.updateById(item);
    }

    private void persistItem(AiDataCleanupItem item) {
        itemMapper.updateById(item);
    }

    private void cancelRemaining(String runId) {
        List<AiDataCleanupItem> remaining = itemMapper.selectList(new LambdaQueryWrapper<AiDataCleanupItem>()
                .eq(AiDataCleanupItem::getRunId, runId)
                .in(AiDataCleanupItem::getStatus, "PENDING", "RUNNING", "FAILED"));
        for (AiDataCleanupItem item : remaining) {
            item.setStatus(CleanupRunStatusEnum.CANCELED.name());
            item.setFinishedAt(LocalDateTime.now());
            itemMapper.updateById(item);
        }
    }

    private boolean timedOut(long startedAt) {
        return elapsed(startedAt) >= properties.getMaxRuntimeSeconds() * 1000L;
    }

    private long elapsed(long startedAt) {
        return Math.max(System.currentTimeMillis() - startedAt, 0L);
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private String safe(RuntimeException exception) {
        String value = exception instanceof BusinessException business
                ? business.getErrorCode().name() : exception.getClass().getSimpleName();
        return value.substring(0, Math.min(value.length(), 1000));
    }
}
