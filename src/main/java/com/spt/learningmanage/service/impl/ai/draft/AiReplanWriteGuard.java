package com.spt.learningmanage.service.impl.ai.draft;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiReplanOperationMapper;
import com.spt.learningmanage.model.entity.AiReplanOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

@Component
public class AiReplanWriteGuard {

    public static final int PREVIEW = 0;
    public static final int CONFIRMED = 1;
    public static final int CANCELED = 2;
    public static final int EXPIRED = 3;

    private final AiReplanOperationMapper operationMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Autowired
    public AiReplanWriteGuard(AiReplanOperationMapper operationMapper,
                              PlatformTransactionManager transactionManager) {
        this(operationMapper, transactionManager, Clock.systemDefaultZone());
    }

    AiReplanWriteGuard(AiReplanOperationMapper operationMapper,
                       PlatformTransactionManager transactionManager,
                       Clock clock) {
        this.operationMapper = operationMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public boolean confirm(Long userId, Long projectId, String operationId, ConfirmWork work) {
        GuardOutcome outcome = Objects.requireNonNull(transactionTemplate.execute(status -> {
            AiReplanOperation operation = operationMapper.selectForUpdate(userId, projectId, operationId);
            if (operation == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "重排操作不存在");
            }
            if (!Objects.equals(operation.getStatus(), PREVIEW)) {
                return GuardOutcome.failure("该重排操作已确认/取消/过期，不能重复确认");
            }
            LocalDateTime now = LocalDateTime.now(clock);
            if (operation.getExpiresAt() != null && !now.isBefore(operation.getExpiresAt())) {
                if (transition(operation.getId(), PREVIEW, EXPIRED, null) != 1) {
                    throw new BusinessException(ErrorCode.AI_DRAFT_CONFLICT, "重排过期状态更新冲突");
                }
                return GuardOutcome.failure("重排预览已过期，请重新预览");
            }
            boolean changed = work.apply(operation);
            if (transition(operation.getId(), PREVIEW, CONFIRMED, now) != 1) {
                throw new BusinessException(ErrorCode.AI_DRAFT_CONFLICT, "重排确认状态更新冲突");
            }
            return GuardOutcome.success(changed);
        }), "重排确认事务未返回结果");
        if (outcome.message() != null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, outcome.message());
        }
        return outcome.changed();
    }

    public boolean cancel(Long userId, String operationId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            AiReplanOperation operation = operationMapper.selectForUpdateByUser(userId, operationId);
            if (operation == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "重排操作不存在");
            }
            if (!Objects.equals(operation.getStatus(), PREVIEW)) {
                return false;
            }
            LocalDateTime now = LocalDateTime.now(clock);
            if (operation.getExpiresAt() != null && !now.isBefore(operation.getExpiresAt())) {
                if (transition(operation.getId(), PREVIEW, EXPIRED, null) != 1) {
                    requireTerminalAfterConflict(operation.getId());
                }
                return false;
            }
            if (transition(operation.getId(), PREVIEW, CANCELED, now) == 1) {
                return true;
            }
            AiReplanOperation winner = requireTerminalAfterConflict(operation.getId());
            return Objects.equals(winner.getStatus(), CANCELED);
        }));
    }

    public int expirePreviewOperations() {
        return operationMapper.update(null, new LambdaUpdateWrapper<AiReplanOperation>()
                .eq(AiReplanOperation::getStatus, PREVIEW)
                .le(AiReplanOperation::getExpiresAt, LocalDateTime.now(clock))
                .set(AiReplanOperation::getStatus, EXPIRED));
    }

    private AiReplanOperation requireTerminalAfterConflict(Long id) {
        AiReplanOperation current = operationMapper.selectById(id);
        if (current == null || Objects.equals(current.getStatus(), PREVIEW)) {
            throw new BusinessException(ErrorCode.AI_DRAFT_CONFLICT, "重排状态更新冲突");
        }
        return current;
    }

    private int transition(Long id, int expected, int target, LocalDateTime transitionTime) {
        LambdaUpdateWrapper<AiReplanOperation> update = new LambdaUpdateWrapper<AiReplanOperation>()
                .eq(AiReplanOperation::getId, id)
                .eq(AiReplanOperation::getStatus, expected)
                .set(AiReplanOperation::getStatus, target);
        if (target == CONFIRMED) {
            update.set(AiReplanOperation::getConfirmedAt, transitionTime);
        } else if (target == CANCELED) {
            update.set(AiReplanOperation::getCanceledAt, transitionTime);
        }
        return operationMapper.update(null, update);
    }

    @FunctionalInterface
    public interface ConfirmWork {
        boolean apply(AiReplanOperation operation);
    }

    private record GuardOutcome(boolean changed, String message) {
        private static GuardOutcome success(boolean changed) {
            return new GuardOutcome(changed, null);
        }

        private static GuardOutcome failure(String message) {
            return new GuardOutcome(false, message);
        }
    }
}
