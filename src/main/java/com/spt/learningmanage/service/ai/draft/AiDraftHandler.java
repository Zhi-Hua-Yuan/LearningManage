package com.spt.learningmanage.service.ai.draft;

import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.dto.ai.draft.AiDraftConfirmationContext;
import com.spt.learningmanage.model.entity.AiDraft;

import java.util.Set;

public interface AiDraftHandler<C extends AiDraftConfirmationContext> {

    String scene();

    int currentSchemaVersion();

    Set<Integer> supportedSchemaVersions();

    Class<C> contextType();

    Long apply(AiDraft draft, C context);

    default Long applyValidated(AiDraft draft, AiDraftConfirmationContext context) {
        if (context == null || !contextType().isInstance(context)) {
            throw new BusinessException(ErrorCode.AI_DRAFT_CONFLICT, "草稿确认上下文类型不匹配");
        }
        return apply(draft, contextType().cast(context));
    }
}
