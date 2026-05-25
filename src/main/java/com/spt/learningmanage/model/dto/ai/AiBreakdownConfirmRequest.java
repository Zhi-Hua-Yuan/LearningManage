package com.spt.learningmanage.model.dto.ai;

import lombok.Data;

@Data
public class AiBreakdownConfirmRequest {
    private String draftId;
    private String operationId;
    private String projectName;
    private String projectGoal;
}
