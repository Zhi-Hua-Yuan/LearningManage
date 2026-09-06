package com.spt.learningmanage.model.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentReportConfirmRequest {
    @NotBlank @Size(max = 64)
    private String draftId;
    @NotBlank @Size(max = 64)
    private String operationId;
}
