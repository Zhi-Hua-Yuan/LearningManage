package com.spt.learningmanage.service.ai.scene;

import com.spt.learningmanage.model.dto.ai.AiPolishRequest;
import com.spt.learningmanage.model.vo.ai.AiDraftConfirmVO;
import com.spt.learningmanage.model.vo.ai.AiPolishPreviewVO;

import java.util.List;

public interface WeeklyReviewAiService {
    String polishWeeklyReview(List<Long> taskIds, String reflection);

    AiPolishPreviewVO previewWeeklyPolish(AiPolishRequest request);

    AiDraftConfirmVO confirmWeeklyPolish(String draftId, String operationId, Long reviewId);
}
