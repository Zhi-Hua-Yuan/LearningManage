package com.spt.learningmanage.service.impl.ai.support;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spt.learningmanage.constant.AiDraftStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiDraftMapper;
import com.spt.learningmanage.model.dto.ai.draft.AiDraftCreateCommand;
import com.spt.learningmanage.model.entity.AiDraft;
import com.spt.learningmanage.model.vo.ai.AiDraftDetailVO;
import com.spt.learningmanage.service.ai.draft.AiDraftHandlerRegistry;
import com.spt.learningmanage.service.ai.support.AiDraftLifecycleService;
import com.spt.learningmanage.service.impl.ai.draft.AiDraftStateMachine;
import com.spt.learningmanage.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class AiDraftLifecycleServiceImpl implements AiDraftLifecycleService {

    private static final int AI_DRAFT_EXPIRE_MINUTES = 20;

    private final AiDraftMapper aiDraftMapper;
    private final AiDraftHandlerRegistry handlerRegistry;
    private final AiDraftStateMachine stateMachine;

    public AiDraftLifecycleServiceImpl(AiDraftMapper aiDraftMapper,
                                       AiDraftHandlerRegistry handlerRegistry,
                                       AiDraftStateMachine stateMachine) {
        this.aiDraftMapper = aiDraftMapper;
        this.handlerRegistry = handlerRegistry;
        this.stateMachine = stateMachine;
    }

    @Override
    public AiDraft createDraft(AiDraftCreateCommand command) {
        validateCreateCommand(command);
        int currentSchemaVersion = handlerRegistry.currentSchemaVersion(command.scene());
        if (!Objects.equals(currentSchemaVersion, command.schemaVersion())) {
            throw new BusinessException(ErrorCode.AI_DRAFT_SCHEMA_UNSUPPORTED,
                    "草稿创建版本与场景当前版本不一致");
        }
        AiDraft draft = new AiDraft();
        draft.setDraftId(UUID.randomUUID().toString().replace("-", ""));
        draft.setUserId(command.userId());
        draft.setScene(command.scene());
        draft.setSchemaVersion(command.schemaVersion());
        draft.setPayloadJson(command.payloadJson());
        draft.setInputHash(command.inputHash());
        draft.setTraceId(StrUtil.blankToDefault(command.traceId(), null));
        draft.setStatus(AiDraftStatusEnum.PREVIEW.getValue());
        int expireMinutes = command.expireMinutes() == null
                ? AI_DRAFT_EXPIRE_MINUTES : command.expireMinutes();
        if (expireMinutes < 1 || expireMinutes > 1440) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "草稿有效期不合法");
        }
        draft.setExpireAt(stateMachine.now().plusMinutes(expireMinutes));
        if (aiDraftMapper.insert(draft) != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "草稿创建失败");
        }
        return draft;
    }

    @Override
    public String buildInputHash(String raw) {
        return Integer.toHexString(Objects.hashCode(raw));
    }

    @Override
    public boolean cancelDraft(String draftId, String scene) {
        Long userId = currentUserId();
        if (StrUtil.isBlank(draftId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "draftId 不能为空");
        }
        AiDraft draft = materializeExpiry(requireDraft(userId, draftId.trim(), scene));
        if (AiDraftStatusEnum.isCanceled(draft.getStatus())) {
            return true;
        }
        rejectCancelTerminalState(draft);
        if (stateMachine.markCanceled(draft.getId())) {
            return true;
        }
        AiDraft winner = requireDraft(userId, draft.getDraftId(), scene);
        if (AiDraftStatusEnum.isCanceled(winner.getStatus())) {
            return true;
        }
        rejectCancelTerminalState(winner);
        throw new BusinessException(ErrorCode.AI_DRAFT_CONFLICT, "草稿取消状态冲突，请刷新后重试");
    }

    @Override
    public AiDraftDetailVO getDraftDetail(String draftId) {
        Long userId = currentUserId();
        if (StrUtil.isBlank(draftId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "draftId 不能为空");
        }
        AiDraft draft = materializeExpiry(requireDraft(userId, draftId.trim(), null));
        AiDraftDetailVO vo = new AiDraftDetailVO();
        vo.setDraftId(draft.getDraftId());
        vo.setScene(draft.getScene());
        vo.setStatus(draft.getStatus());
        vo.setStatusText(AiDraftStatusEnum.getText(draft.getStatus()));
        vo.setPayloadJson(draft.getPayloadJson());
        vo.setExpireAt(draft.getExpireAt());
        vo.setConfirmedAt(draft.getConfirmedAt());
        vo.setCanceledAt(draft.getCanceledAt());
        return vo;
    }

    @Override
    public int expirePreviewDrafts() {
        return stateMachine.expirePreviewsBefore(stateMachine.now());
    }

    private AiDraft requireDraft(Long userId, String draftId, String scene) {
        LambdaQueryWrapper<AiDraft> wrapper = new LambdaQueryWrapper<AiDraft>()
                .eq(AiDraft::getDraftId, draftId)
                .eq(AiDraft::getUserId, userId);
        if (StrUtil.isNotBlank(scene)) {
            wrapper.eq(AiDraft::getScene, scene.trim());
        }
        AiDraft draft = aiDraftMapper.selectOne(wrapper.last("limit 1"));
        if (draft == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "草稿不存在");
        }
        return draft;
    }

    private AiDraft materializeExpiry(AiDraft draft) {
        if (!AiDraftStatusEnum.isPreview(draft.getStatus()) || !stateMachine.isExpired(draft)) {
            return draft;
        }
        if (stateMachine.markExpired(draft.getId())) {
            draft.setStatus(AiDraftStatusEnum.EXPIRED.getValue());
            return draft;
        }
        return requireDraft(draft.getUserId(), draft.getDraftId(), draft.getScene());
    }

    private void rejectCancelTerminalState(AiDraft draft) {
        if (AiDraftStatusEnum.isConfirmed(draft.getStatus())) {
            throw new BusinessException(ErrorCode.AI_DRAFT_NOT_CONFIRMABLE, "草稿已确认，不能取消");
        }
        if (AiDraftStatusEnum.isExpired(draft.getStatus())) {
            throw new BusinessException(ErrorCode.AI_DRAFT_EXPIRED, "草稿已过期，不能取消");
        }
        if (!AiDraftStatusEnum.isPreview(draft.getStatus())) {
            throw new BusinessException(ErrorCode.AI_DRAFT_CONFLICT, "草稿状态异常，不能取消");
        }
    }

    private void validateCreateCommand(AiDraftCreateCommand command) {
        if (command == null || command.userId() == null || command.userId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "草稿用户不合法");
        }
        if (StrUtil.hasBlank(command.scene(), command.payloadJson(), command.inputHash())
                || command.schemaVersion() == null || command.schemaVersion() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "草稿创建参数不合法");
        }
    }

    private Long currentUserId() {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
        return userId;
    }
}
