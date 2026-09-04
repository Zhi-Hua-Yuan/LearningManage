package com.spt.learningmanage.service.impl.ai.support;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.spt.learningmanage.constant.AiDraftStatusEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.AiDraftConfirmLogMapper;
import com.spt.learningmanage.mapper.AiDraftMapper;
import com.spt.learningmanage.model.entity.AiDraft;
import com.spt.learningmanage.model.entity.AiDraftConfirmLog;
import com.spt.learningmanage.model.vo.ai.AiDraftConfirmVO;
import com.spt.learningmanage.model.vo.ai.AiDraftDetailVO;
import com.spt.learningmanage.service.ai.support.AiDraftLifecycleService;
import com.spt.learningmanage.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class AiDraftLifecycleServiceImpl implements AiDraftLifecycleService {

    private static final int AI_DRAFT_EXPIRE_MINUTES = 20;

    private final AiDraftMapper aiDraftMapper;
    private final AiDraftConfirmLogMapper aiDraftConfirmLogMapper;

    public AiDraftLifecycleServiceImpl(AiDraftMapper aiDraftMapper,
                                       AiDraftConfirmLogMapper aiDraftConfirmLogMapper) {
        this.aiDraftMapper = aiDraftMapper;
        this.aiDraftConfirmLogMapper = aiDraftConfirmLogMapper;
    }

    @Override
    public AiDraft createDraft(Long userId, String scene, String payloadJson, String inputHash) {
        AiDraft draft = new AiDraft();
        draft.setDraftId(UUID.randomUUID().toString().replace("-", ""));
        draft.setUserId(userId);
        draft.setScene(scene);
        draft.setPayloadJson(payloadJson);
        draft.setInputHash(inputHash);
        draft.setStatus(AiDraftStatusEnum.PREVIEW.getValue());
        draft.setExpireAt(LocalDateTime.now().plusMinutes(AI_DRAFT_EXPIRE_MINUTES));
        if (aiDraftMapper.insert(draft) != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "草稿创建失败");
        }
        return draft;
    }

    @Override
    public AiDraft requireDraft(Long userId, String draftId, String scene) {
        LambdaQueryWrapper<AiDraft> wrapper = new LambdaQueryWrapper<AiDraft>()
                .eq(AiDraft::getDraftId, draftId)
                .eq(AiDraft::getUserId, userId);
        if (StrUtil.isNotBlank(scene)) {
            wrapper.eq(AiDraft::getScene, scene);
        }
        AiDraft draft = aiDraftMapper.selectOne(wrapper.last("limit 1"));
        if (draft == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "草稿不存在");
        }
        return draft;
    }

    @Override
    public AiDraftConfirmLog findConfirmLog(Long userId, String draftId, String operationId) {
        return aiDraftConfirmLogMapper.selectOne(new LambdaQueryWrapper<AiDraftConfirmLog>()
                .eq(AiDraftConfirmLog::getUserId, userId)
                .eq(AiDraftConfirmLog::getDraftId, draftId)
                .eq(AiDraftConfirmLog::getOperationId, operationId)
                .last("limit 1"));
    }

    @Override
    public void requireConfirmable(AiDraft draft) {
        if (refreshExpiredIfNecessary(draft)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "草稿已过期，请重新预览");
        }
        if (AiDraftStatusEnum.isPreview(draft.getStatus())) {
            return;
        }
        if (AiDraftStatusEnum.isConfirmed(draft.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "草稿已确认，请勿重复提交");
        }
        if (AiDraftStatusEnum.isCanceled(draft.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "草稿已取消，请重新生成");
        }
        if (AiDraftStatusEnum.isExpired(draft.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "草稿已过期，请重新预览");
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR,
                "草稿状态异常，无法确认：" + AiDraftStatusEnum.getText(draft.getStatus()));
    }

    @Override
    public void markConfirmed(Long draftDbId) {
        int rows = aiDraftMapper.update(null, new LambdaUpdateWrapper<AiDraft>()
                .eq(AiDraft::getId, draftDbId)
                .eq(AiDraft::getStatus, AiDraftStatusEnum.PREVIEW.getValue())
                .set(AiDraft::getStatus, AiDraftStatusEnum.CONFIRMED.getValue())
                .set(AiDraft::getConfirmedAt, LocalDateTime.now()));
        if (rows != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "草稿状态更新失败");
        }
    }

    @Override
    public void insertConfirmLog(Long userId, String draftId, String operationId, String scene, Long businessId) {
        AiDraftConfirmLog logEntity = new AiDraftConfirmLog();
        logEntity.setUserId(userId);
        logEntity.setDraftId(draftId);
        logEntity.setOperationId(operationId);
        logEntity.setScene(scene);
        logEntity.setBusinessId(businessId);
        try {
            aiDraftConfirmLogMapper.insert(logEntity);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "确认日志写入失败");
        }
    }

    @Override
    public AiDraftConfirmVO buildConfirmResult(boolean replay, Long businessId) {
        AiDraftConfirmVO vo = new AiDraftConfirmVO();
        vo.setSuccess(true);
        vo.setIdempotentReplay(replay);
        vo.setBusinessId(businessId);
        return vo;
    }

    @Override
    public String buildInputHash(String raw) {
        return Integer.toHexString(Objects.hashCode(raw));
    }

    @Override
    public boolean cancelDraft(String draftId, String scene) {
        Long userId = currentUserId();
        AiDraft draft = requireDraft(userId, draftId, scene);
        refreshExpiredIfNecessary(draft);
        if (AiDraftStatusEnum.isCanceled(draft.getStatus())) {
            return true;
        }
        if (AiDraftStatusEnum.isConfirmed(draft.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "草稿已确认，不能取消");
        }
        if (AiDraftStatusEnum.isExpired(draft.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "草稿已过期，不能取消");
        }
        if (!AiDraftStatusEnum.isPreview(draft.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "草稿状态异常，不能取消");
        }
        return aiDraftMapper.update(null, new LambdaUpdateWrapper<AiDraft>()
                .eq(AiDraft::getId, draft.getId())
                .eq(AiDraft::getStatus, AiDraftStatusEnum.PREVIEW.getValue())
                .set(AiDraft::getStatus, AiDraftStatusEnum.CANCELED.getValue())
                .set(AiDraft::getCanceledAt, LocalDateTime.now())) > 0;
    }

    @Override
    public AiDraftDetailVO getDraftDetail(String draftId) {
        Long userId = currentUserId();
        if (StrUtil.isBlank(draftId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "draftId 不能为空");
        }
        AiDraft draft = requireDraft(userId, draftId.trim(), null);
        refreshExpiredIfNecessary(draft);
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
        return aiDraftMapper.update(null, new LambdaUpdateWrapper<AiDraft>()
                .eq(AiDraft::getStatus, AiDraftStatusEnum.PREVIEW.getValue())
                .lt(AiDraft::getExpireAt, LocalDateTime.now())
                .set(AiDraft::getStatus, AiDraftStatusEnum.EXPIRED.getValue()));
    }

    private boolean refreshExpiredIfNecessary(AiDraft draft) {
        if (!AiDraftStatusEnum.isPreview(draft.getStatus())) {
            return false;
        }
        if (draft.getExpireAt() != null && LocalDateTime.now().isAfter(draft.getExpireAt())) {
            int rows = aiDraftMapper.update(null, new LambdaUpdateWrapper<AiDraft>()
                    .eq(AiDraft::getId, draft.getId())
                    .eq(AiDraft::getStatus, AiDraftStatusEnum.PREVIEW.getValue())
                    .set(AiDraft::getStatus, AiDraftStatusEnum.EXPIRED.getValue()));
            if (rows > 0) {
                draft.setStatus(AiDraftStatusEnum.EXPIRED.getValue());
            }
            return true;
        }
        return false;
    }

    private Long currentUserId() {
        Long userId = UserHolder.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
        return userId;
    }
}
