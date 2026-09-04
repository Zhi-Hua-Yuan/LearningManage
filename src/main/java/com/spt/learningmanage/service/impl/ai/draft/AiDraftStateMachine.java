package com.spt.learningmanage.service.impl.ai.draft;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.spt.learningmanage.constant.AiDraftStatusEnum;
import com.spt.learningmanage.mapper.AiDraftMapper;
import com.spt.learningmanage.model.entity.AiDraft;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class AiDraftStateMachine {

    private final AiDraftMapper aiDraftMapper;
    private final Clock clock;

    @Autowired
    public AiDraftStateMachine(AiDraftMapper aiDraftMapper) {
        this(aiDraftMapper, Clock.systemDefaultZone());
    }

    public AiDraftStateMachine(AiDraftMapper aiDraftMapper, Clock clock) {
        this.aiDraftMapper = aiDraftMapper;
        this.clock = clock;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public boolean isExpired(AiDraft draft) {
        return draft.getExpireAt() != null && !now().isBefore(draft.getExpireAt());
    }

    public boolean markConfirmed(Long draftDbId) {
        return transition(draftDbId, AiDraftStatusEnum.CONFIRMED, now()) == 1;
    }

    public boolean markCanceled(Long draftDbId) {
        return transition(draftDbId, AiDraftStatusEnum.CANCELED, now()) == 1;
    }

    public boolean markExpired(Long draftDbId) {
        return transition(draftDbId, AiDraftStatusEnum.EXPIRED, null) == 1;
    }

    public int expirePreviewsBefore(LocalDateTime cutoff) {
        return aiDraftMapper.update(null, new LambdaUpdateWrapper<AiDraft>()
                .eq(AiDraft::getStatus, AiDraftStatusEnum.PREVIEW.getValue())
                .le(AiDraft::getExpireAt, cutoff)
                .set(AiDraft::getStatus, AiDraftStatusEnum.EXPIRED.getValue()));
    }

    private int transition(Long draftDbId, AiDraftStatusEnum target, LocalDateTime transitionTime) {
        LambdaUpdateWrapper<AiDraft> update = new LambdaUpdateWrapper<AiDraft>()
                .eq(AiDraft::getId, draftDbId)
                .eq(AiDraft::getStatus, AiDraftStatusEnum.PREVIEW.getValue())
                .set(AiDraft::getStatus, target.getValue());
        if (target == AiDraftStatusEnum.CONFIRMED) {
            update.set(AiDraft::getConfirmedAt, transitionTime);
        } else if (target == AiDraftStatusEnum.CANCELED) {
            update.set(AiDraft::getCanceledAt, transitionTime);
        }
        return aiDraftMapper.update(null, update);
    }
}
