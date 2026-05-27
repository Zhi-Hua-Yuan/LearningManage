package com.spt.learningmanage.model.dto.ai;

import lombok.Data;

@Data
public class AiPolishConfirmRequest {
    private String draftId;
    private String operationId;
    private Long reviewId;
}
