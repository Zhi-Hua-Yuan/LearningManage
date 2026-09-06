package com.spt.learningmanage.model.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentTeamWorkloadRequest {
    @NotNull @Positive
    private Long teamId;
    @NotBlank @Size(max = 64)
    private String clientRequestId;
}
