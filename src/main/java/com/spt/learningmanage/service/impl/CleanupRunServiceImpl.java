package com.spt.learningmanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.spt.learningmanage.config.DataCleanupProperties;
import com.spt.learningmanage.constant.CleanupResourceTypeEnum;
import com.spt.learningmanage.constant.CleanupRunStatusEnum;
import com.spt.learningmanage.constant.CleanupTriggerTypeEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiDataCleanupItemMapper;
import com.spt.learningmanage.mapper.AiDataCleanupRunMapper;
import com.spt.learningmanage.model.dto.ops.CleanupRunCreateRequest;
import com.spt.learningmanage.model.entity.AiDataCleanupItem;
import com.spt.learningmanage.model.entity.AiDataCleanupRun;
import com.spt.learningmanage.model.vo.ops.CleanupCancelVO;
import com.spt.learningmanage.model.vo.ops.CleanupItemVO;
import com.spt.learningmanage.model.vo.ops.CleanupRunVO;
import com.spt.learningmanage.service.AdminOperationAuditService;
import com.spt.learningmanage.service.CleanupRunService;
import com.spt.learningmanage.service.DataRetentionPolicy;
import com.spt.learningmanage.service.PermissionService;
import com.spt.learningmanage.trace.TraceContext;
import com.spt.learningmanage.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class CleanupRunServiceImpl implements CleanupRunService {
    private static final Set<CleanupResourceTypeEnum> DEFAULT_RESOURCES =
            EnumSet.allOf(CleanupResourceTypeEnum.class);

    private final AiDataCleanupRunMapper runMapper;
    private final AiDataCleanupItemMapper itemMapper;
    private final DataCleanupProperties properties;
    private final DataRetentionPolicy retentionPolicy;
    private final PermissionService permissionService;
    private final AdminOperationAuditService auditService;

    public CleanupRunServiceImpl(AiDataCleanupRunMapper runMapper,
                                 AiDataCleanupItemMapper itemMapper,
                                 DataCleanupProperties properties,
                                 DataRetentionPolicy retentionPolicy,
                                 PermissionService permissionService,
                                 AdminOperationAuditService auditService) {
        this.runMapper = runMapper;
        this.itemMapper = itemMapper;
        this.properties = properties;
        this.retentionPolicy = retentionPolicy;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CleanupRunVO submit(CleanupRunCreateRequest request) {
        Long actor = requireAdmin();
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.CLEANUP_DISABLED);
        }
        if (request == null || request.getClientRequestId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        requireSubmissionLock();
        AiDataCleanupRun existing = runMapper.selectByRequestForUpdate(
                actor, request.getClientRequestId().trim());
        if (existing != null) {
            return toVO(existing, true);
        }
        return create(actor, CleanupTriggerTypeEnum.MANUAL,
                request.getClientRequestId().trim(), !Boolean.FALSE.equals(request.getDryRun()),
                normalizeResources(request.getResourceTypes()));
    }

    @Override
    public CleanupRunVO get(String runId) {
        requireAdmin();
        return toVO(requireRun(runId), false, true);
    }

    @Override
    public Page<CleanupRunVO> list(long current, long size) {
        requireAdmin();
        long pageNo = Math.max(current, 1);
        long pageSize = Math.max(1, Math.min(size, 100));
        Page<AiDataCleanupRun> page = runMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<AiDataCleanupRun>().orderByDesc(AiDataCleanupRun::getCreateTime));
        Page<CleanupRunVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(run -> toVO(run, false, false)).toList());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CleanupCancelVO cancel(String runId) {
        Long actor = requireAdmin();
        AiDataCleanupRun run = requireRun(runId);
        LocalDateTime now = LocalDateTime.now();
        if (CleanupRunStatusEnum.PENDING.name().equals(run.getStatus())
                && runMapper.cancelPending(run.getId(), now) == 1) {
            itemMapper.update(null, new LambdaUpdateWrapper<AiDataCleanupItem>()
                    .eq(AiDataCleanupItem::getRunId, run.getRunId())
                    .eq(AiDataCleanupItem::getStatus, CleanupRunStatusEnum.PENDING.name())
                    .set(AiDataCleanupItem::getStatus, CleanupRunStatusEnum.CANCELED.name())
                    .set(AiDataCleanupItem::getFinishedAt, now));
            auditService.success(actor, "CLEANUP_CANCEL", "CLEANUP_RUN", runId,
                    "pending", "canceled");
            return new CleanupCancelVO(runId, CleanupRunStatusEnum.CANCELED.name(), false);
        }
        if (CleanupRunStatusEnum.RUNNING.name().equals(run.getStatus())
                && runMapper.requestCancelRunning(run.getId(), now) == 1) {
            auditService.success(actor, "CLEANUP_CANCEL", "CLEANUP_RUN", runId,
                    "running", "cancellation_requested");
            return new CleanupCancelVO(runId, CleanupRunStatusEnum.RUNNING.name(), true);
        }
        throw new BusinessException(ErrorCode.CLEANUP_RUN_NOT_CANCELABLE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitScheduled() {
        if (!properties.isEnabled() || !properties.isScheduleEnabled()) {
            return;
        }
        requireSubmissionLock();
        String requestId = "scheduled-dry-" + LocalDate.now();
        if (runMapper.selectByRequestForUpdate(null, requestId) != null) {
            return;
        }
        if (runMapper.selectActiveForUpdate() != null) {
            return;
        }
        List<CleanupResourceTypeEnum> resources = ordered(DEFAULT_RESOURCES);
        createInternal(null, CleanupTriggerTypeEnum.SCHEDULED, requestId,
                true, resources, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitScheduledFormal(String approvedDryRunId) {
        if (!properties.isEnabled() || !properties.isScheduleEnabled()) {
            return;
        }
        requireSubmissionLock();
        AiDataCleanupRun approved = runMapper.selectOne(new LambdaQueryWrapper<AiDataCleanupRun>()
                .eq(AiDataCleanupRun::getRunId, approvedDryRunId).last("limit 1 for update"));
        if (approved == null) {
            return;
        }
        if (!CleanupTriggerTypeEnum.SCHEDULED.name().equals(approved.getTriggerType())
                || approved.getDryRun() != 1
                || !CleanupRunStatusEnum.SUCCEEDED.name().equals(approved.getStatus())) {
            return;
        }
        String requestId = "scheduled-formal-" + LocalDate.now();
        if (runMapper.selectByRequestForUpdate(null, requestId) != null
                || runMapper.selectActiveForUpdate() != null) {
            return;
        }
        List<CleanupResourceTypeEnum> resources = itemMapper.selectList(
                        new LambdaQueryWrapper<AiDataCleanupItem>()
                                .eq(AiDataCleanupItem::getRunId, approved.getRunId()))
                .stream().map(item -> CleanupResourceTypeEnum.valueOf(item.getResourceType()))
                .sorted(Comparator.comparingInt(Enum::ordinal)).toList();
        createInternal(null, CleanupTriggerTypeEnum.SCHEDULED, requestId,
                false, resources, approved);
    }

    private CleanupRunVO create(Long actor,
                                CleanupTriggerTypeEnum trigger,
                                String clientRequestId,
                                boolean dryRun,
                                List<CleanupResourceTypeEnum> resources) {
        if (runMapper.selectActiveForUpdate() != null) {
            throw new BusinessException(ErrorCode.CLEANUP_ALREADY_RUNNING);
        }
        String hash = resourceHash(resources);
        AiDataCleanupRun approved = dryRun ? null : latestDryRun(hash);
        if (!dryRun && approved == null) {
            throw new BusinessException(ErrorCode.CLEANUP_DRY_RUN_REQUIRED);
        }
        CleanupRunVO result = createInternal(actor, trigger, clientRequestId, dryRun, resources, approved);
        auditService.success(actor, "CLEANUP_SUBMIT", "CLEANUP_RUN", result.getRunId(),
                "dryRun=" + dryRun + ";resources=" + resources.size(), "PENDING");
        return result;
    }

    private CleanupRunVO createInternal(Long actor,
                                        CleanupTriggerTypeEnum trigger,
                                        String clientRequestId,
                                        boolean dryRun,
                                        List<CleanupResourceTypeEnum> resources,
                                        AiDataCleanupRun approved) {
        LocalDateTime now = LocalDateTime.now();
        java.util.Map<String, LocalDateTime> approvedCutoffs = approved == null
                ? java.util.Map.of()
                : itemMapper.selectList(new LambdaQueryWrapper<AiDataCleanupItem>()
                        .eq(AiDataCleanupItem::getRunId, approved.getRunId()))
                        .stream().collect(java.util.stream.Collectors.toMap(
                                AiDataCleanupItem::getResourceType, AiDataCleanupItem::getCutoffTime));
        AiDataCleanupRun run = new AiDataCleanupRun();
        run.setId(IdWorker.getId());
        run.setRunId("cleanup_" + UUID.randomUUID().toString().replace("-", ""));
        run.setClientRequestId(clientRequestId);
        run.setInitiatorUserId(actor);
        run.setTriggerType(trigger.name());
        run.setPolicyVersion(retentionPolicy.version());
        run.setResourceHash(resourceHash(resources));
        run.setApprovedDryRunId(approved == null ? null : approved.getId());
        run.setDryRun(dryRun ? 1 : 0);
        run.setStatus(CleanupRunStatusEnum.PENDING.name());
        run.setScannedCount(0L);
        run.setEstimatedCount(0L);
        run.setAffectedCount(0L);
        run.setFailureCount(0L);
        run.setTraceId(TraceContext.currentOrCreate());
        try {
            runMapper.insert(run);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CLEANUP_ALREADY_RUNNING);
        }
        for (CleanupResourceTypeEnum resource : resources) {
            AiDataCleanupItem item = new AiDataCleanupItem();
            item.setId(IdWorker.getId());
            item.setRunId(run.getRunId());
            item.setResourceType(resource.name());
            item.setCutoffTime(approvedCutoffs.getOrDefault(resource.name(),
                    retentionPolicy.cutoff(resource, now)));
            item.setStatus(CleanupRunStatusEnum.PENDING.name());
            item.setCursorId(0L);
            item.setScannedCount(0L);
            item.setEstimatedCount(0L);
            item.setRedactedCount(0L);
            item.setDeletedCount(0L);
            itemMapper.insert(item);
        }
        return toVO(run, false, true);
    }

    private AiDataCleanupRun latestDryRun(String resourceHash) {
        return runMapper.selectLatestDryRunForUpdate(retentionPolicy.version(), resourceHash,
                LocalDateTime.now().minusHours(properties.getDryRunValidHours()));
    }

    private AiDataCleanupRun requireRun(String runId) {
        if (runId == null || runId.isBlank() || runId.length() > 64) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "runId 不合法");
        }
        AiDataCleanupRun run = runMapper.selectOne(new LambdaQueryWrapper<AiDataCleanupRun>()
                .eq(AiDataCleanupRun::getRunId, runId.trim()).last("limit 1"));
        if (run == null) {
            throw new BusinessException(ErrorCode.CLEANUP_RUN_NOT_FOUND);
        }
        return run;
    }

    private List<CleanupResourceTypeEnum> normalizeResources(List<String> values) {
        if (values == null || values.isEmpty()) {
            return ordered(DEFAULT_RESOURCES);
        }
        EnumSet<CleanupResourceTypeEnum> result = EnumSet.noneOf(CleanupResourceTypeEnum.class);
        try {
            values.forEach(value -> result.add(CleanupResourceTypeEnum.valueOf(
                    value.trim().toUpperCase(Locale.ROOT))));
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "resourceTypes 不合法");
        }
        return ordered(result);
    }

    private List<CleanupResourceTypeEnum> ordered(Set<CleanupResourceTypeEnum> resources) {
        return resources.stream().sorted(Comparator.comparingInt(Enum::ordinal)).toList();
    }

    private String resourceHash(List<CleanupResourceTypeEnum> resources) {
        try {
            String canonical = resources.stream().map(Enum::name).sorted().reduce((a, b) -> a + "," + b).orElse("");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private Long requireAdmin() {
        Long actor = UserHolder.get();
        if (actor == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        permissionService.requireSystemAdmin(actor);
        return actor;
    }

    private void requireSubmissionLock() {
        if (!Integer.valueOf(1).equals(runMapper.lockSubmission())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "无法获取清理任务提交锁");
        }
    }

    private CleanupRunVO toVO(AiDataCleanupRun run, boolean replay) {
        return toVO(run, replay, true);
    }

    private CleanupRunVO toVO(AiDataCleanupRun run, boolean replay, boolean includeItems) {
        CleanupRunVO vo = new CleanupRunVO();
        BeanUtils.copyProperties(run, vo);
        vo.setDryRun(run.getDryRun() != null && run.getDryRun() == 1);
        vo.setIdempotentReplay(replay);
        if (!includeItems) {
            vo.setItems(List.of());
            return vo;
        }
        List<AiDataCleanupItem> items = itemMapper.selectList(new LambdaQueryWrapper<AiDataCleanupItem>()
                .eq(AiDataCleanupItem::getRunId, run.getRunId()).orderByAsc(AiDataCleanupItem::getId));
        List<CleanupItemVO> itemVOs = new ArrayList<>();
        for (AiDataCleanupItem item : items) {
            CleanupItemVO itemVO = new CleanupItemVO();
            BeanUtils.copyProperties(item, itemVO);
            itemVOs.add(itemVO);
        }
        vo.setItems(itemVOs);
        return vo;
    }
}
