package com.spt.learningmanage.service.impl.ai.draft;

import cn.hutool.core.util.StrUtil;
import com.spt.learningmanage.constant.AiDraftStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiDraftConfirmLogMapper;
import com.spt.learningmanage.mapper.AiDraftMapper;
import com.spt.learningmanage.model.dto.ai.draft.AiDraftConfirmationCommand;
import com.spt.learningmanage.model.entity.AiDraft;
import com.spt.learningmanage.model.entity.AiDraftConfirmLog;
import com.spt.learningmanage.model.vo.ai.AiDraftConfirmVO;
import com.spt.learningmanage.service.ai.draft.AiDraftConfirmationService;
import com.spt.learningmanage.service.ai.draft.AiDraftHandler;
import com.spt.learningmanage.service.ai.draft.AiDraftHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

@Service
public class AiDraftConfirmationServiceImpl implements AiDraftConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(AiDraftConfirmationServiceImpl.class);
    private static final int IDENTIFIER_MAX_LENGTH = 64;

    private final AiDraftMapper aiDraftMapper;
    private final AiDraftConfirmLogMapper confirmLogMapper;
    private final AiDraftHandlerRegistry handlerRegistry;
    private final AiDraftStateMachine stateMachine;
    private final TransactionTemplate transactionTemplate;

    public AiDraftConfirmationServiceImpl(AiDraftMapper aiDraftMapper,
                                          AiDraftConfirmLogMapper confirmLogMapper,
                                          AiDraftHandlerRegistry handlerRegistry,
                                          AiDraftStateMachine stateMachine,
                                          PlatformTransactionManager transactionManager) {
        this.aiDraftMapper = aiDraftMapper;
        this.confirmLogMapper = confirmLogMapper;
        this.handlerRegistry = handlerRegistry;
        this.stateMachine = stateMachine;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public AiDraftConfirmVO confirm(AiDraftConfirmationCommand command) {
        validate(command);
        AiDraftHandler<?> handler = handlerRegistry.require(command.scene(), command.context());
        ConfirmationOutcome outcome = Objects.requireNonNull(transactionTemplate.execute(status ->
                confirmInTransaction(command, handler)), "草稿确认事务未返回结果");
        if (outcome.errorCode() != null) {
            throw new BusinessException(outcome.errorCode(), outcome.message());
        }
        return buildResult(outcome.replay(), outcome.businessId());
    }

    private ConfirmationOutcome confirmInTransaction(AiDraftConfirmationCommand command,
                                                      AiDraftHandler<?> handler) {
        String draftId = command.draftId().trim();
        AiDraft draft = aiDraftMapper.selectForUpdate(
                command.userId(), draftId, command.scene());
        if (draft == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "草稿不存在");
        }

        AiDraftConfirmLog existing = confirmLogMapper.selectByUserAndDraft(command.userId(), draftId);
        if (existing != null) {
            if (!AiDraftStatusEnum.isConfirmed(draft.getStatus())
                    || !Objects.equals(existing.getScene(), draft.getScene())
                    || !Objects.equals(existing.getScene(), command.scene())
                    || existing.getBusinessId() == null) {
                throw invariantFailure(draft, "确认日志与草稿终态不一致");
            }
            return ConfirmationOutcome.success(true, existing.getBusinessId());
        }

        if (draft.getSchemaVersion() == null
                || !handler.supportedSchemaVersions().contains(draft.getSchemaVersion())) {
            return ConfirmationOutcome.failure(ErrorCode.AI_DRAFT_SCHEMA_UNSUPPORTED,
                    "草稿版本不受支持，请重新生成");
        }
        if (AiDraftStatusEnum.isConfirmed(draft.getStatus())) {
            throw invariantFailure(draft, "草稿已确认但缺少确认日志");
        }
        if (AiDraftStatusEnum.isCanceled(draft.getStatus())) {
            return ConfirmationOutcome.failure(ErrorCode.AI_DRAFT_NOT_CONFIRMABLE,
                    "草稿已取消，请重新生成");
        }
        if (AiDraftStatusEnum.isExpired(draft.getStatus())) {
            return ConfirmationOutcome.failure(ErrorCode.AI_DRAFT_EXPIRED,
                    "草稿已过期，请重新预览");
        }
        if (!AiDraftStatusEnum.isPreview(draft.getStatus())) {
            throw invariantFailure(draft, "草稿状态未知");
        }
        if (stateMachine.isExpired(draft)) {
            if (!stateMachine.markExpired(draft.getId())) {
                throw invariantFailure(draft, "草稿过期状态更新冲突");
            }
            return ConfirmationOutcome.failure(ErrorCode.AI_DRAFT_EXPIRED,
                    "草稿已过期，请重新预览");
        }

        Long businessId = handler.applyValidated(draft, command.context());
        if (businessId == null || businessId <= 0) {
            throw invariantFailure(draft, "草稿 Handler 未返回有效业务ID");
        }

        AiDraftConfirmLog confirmation = new AiDraftConfirmLog();
        confirmation.setUserId(command.userId());
        confirmation.setDraftId(draftId);
        confirmation.setOperationId(command.operationId().trim());
        confirmation.setScene(command.scene());
        confirmation.setBusinessId(businessId);
        confirmation.setTraceId(draft.getTraceId());
        if (confirmLogMapper.insert(confirmation) != 1) {
            throw invariantFailure(draft, "确认日志写入失败");
        }
        if (!stateMachine.markConfirmed(draft.getId())) {
            throw invariantFailure(draft, "草稿确认状态更新冲突");
        }
        return ConfirmationOutcome.success(false, businessId);
    }

    private void validate(AiDraftConfirmationCommand command) {
        if (command == null || command.userId() == null || command.userId() <= 0) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
        if (StrUtil.hasBlank(command.draftId(), command.operationId(), command.scene())
                || command.draftId().trim().length() > IDENTIFIER_MAX_LENGTH
                || command.operationId().trim().length() > IDENTIFIER_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "草稿确认参数不合法");
        }
    }

    private BusinessException invariantFailure(AiDraft draft, String reason) {
        log.error("AI草稿确认不变量失败: draftId={}, scene={}, status={}, reason={}",
                draft.getDraftId(), draft.getScene(), draft.getStatus(), reason);
        return new BusinessException(ErrorCode.AI_DRAFT_CONFLICT, "草稿状态冲突，请刷新后重试");
    }

    private AiDraftConfirmVO buildResult(boolean replay, Long businessId) {
        AiDraftConfirmVO result = new AiDraftConfirmVO();
        result.setSuccess(true);
        result.setIdempotentReplay(replay);
        result.setBusinessId(businessId);
        return result;
    }

    private record ConfirmationOutcome(boolean replay,
                                       Long businessId,
                                       ErrorCode errorCode,
                                       String message) {

        private static ConfirmationOutcome success(boolean replay, Long businessId) {
            return new ConfirmationOutcome(replay, businessId, null, null);
        }

        private static ConfirmationOutcome failure(ErrorCode errorCode, String message) {
            return new ConfirmationOutcome(false, null, errorCode, message);
        }
    }
}
