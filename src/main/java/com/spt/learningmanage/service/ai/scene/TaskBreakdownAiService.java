package com.spt.learningmanage.service.ai.scene;

import com.spt.learningmanage.model.dto.ai.AiBreakdownRequest;
import com.spt.learningmanage.model.vo.ai.AiBreakdownPreviewVO;
import com.spt.learningmanage.model.vo.ai.AiDraftConfirmVO;
import com.spt.learningmanage.model.vo.milestone.MilestoneDraftVO;

import java.util.List;

public interface TaskBreakdownAiService {
    List<MilestoneDraftVO> generateTaskBreakdown(String target, String description, String duration, boolean detailed);

    AiBreakdownPreviewVO previewTaskBreakdown(AiBreakdownRequest request);

    AiDraftConfirmVO confirmTaskBreakdown(String draftId, String operationId, String projectName, String projectGoal);
}
