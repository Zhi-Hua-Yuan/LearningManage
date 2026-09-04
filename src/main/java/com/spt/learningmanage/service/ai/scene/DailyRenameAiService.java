package com.spt.learningmanage.service.ai.scene;

import com.spt.learningmanage.model.dto.ai.DailyReviewSuggestRenameRequest;
import com.spt.learningmanage.model.vo.ai.DailyReviewSuggestRenameVO;

public interface DailyRenameAiService {
    DailyReviewSuggestRenameVO suggestDailyReviewRename(DailyReviewSuggestRenameRequest request);
}
