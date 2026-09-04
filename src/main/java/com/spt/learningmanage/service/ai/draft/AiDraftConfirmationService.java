package com.spt.learningmanage.service.ai.draft;

import com.spt.learningmanage.model.dto.ai.draft.AiDraftConfirmationCommand;
import com.spt.learningmanage.model.vo.ai.AiDraftConfirmVO;

public interface AiDraftConfirmationService {

    AiDraftConfirmVO confirm(AiDraftConfirmationCommand command);
}
