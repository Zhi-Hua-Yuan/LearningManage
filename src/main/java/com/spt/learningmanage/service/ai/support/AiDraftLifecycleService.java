package com.spt.learningmanage.service.ai.support;

import com.spt.learningmanage.model.entity.AiDraft;
import com.spt.learningmanage.model.entity.AiDraftConfirmLog;
import com.spt.learningmanage.model.vo.ai.AiDraftConfirmVO;
import com.spt.learningmanage.model.vo.ai.AiDraftDetailVO;

public interface AiDraftLifecycleService {
    AiDraft createDraft(Long userId, String scene, String payloadJson, String inputHash);

    AiDraft requireDraft(Long userId, String draftId, String scene);

    AiDraftConfirmLog findConfirmLog(Long userId, String draftId, String operationId);

    void requireConfirmable(AiDraft draft);

    void markConfirmed(Long draftDbId);

    void insertConfirmLog(Long userId, String draftId, String operationId, String scene, Long businessId);

    AiDraftConfirmVO buildConfirmResult(boolean replay, Long businessId);

    String buildInputHash(String raw);

    boolean cancelDraft(String draftId, String scene);

    AiDraftDetailVO getDraftDetail(String draftId);

    int expirePreviewDrafts();
}
