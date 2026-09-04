package com.spt.learningmanage.service.ai.support;

import com.spt.learningmanage.model.entity.AiDraft;
import com.spt.learningmanage.model.dto.ai.draft.AiDraftCreateCommand;
import com.spt.learningmanage.model.vo.ai.AiDraftDetailVO;

public interface AiDraftLifecycleService {
    AiDraft createDraft(AiDraftCreateCommand command);

    String buildInputHash(String raw);

    boolean cancelDraft(String draftId, String scene);

    AiDraftDetailVO getDraftDetail(String draftId);

    int expirePreviewDrafts();
}
