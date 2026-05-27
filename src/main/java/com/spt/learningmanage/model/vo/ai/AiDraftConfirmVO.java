package com.spt.learningmanage.model.vo.ai;

import lombok.Data;

@Data
public class AiDraftConfirmVO {
    private Boolean success;
    private Boolean idempotentReplay;
    private Long businessId;
}
