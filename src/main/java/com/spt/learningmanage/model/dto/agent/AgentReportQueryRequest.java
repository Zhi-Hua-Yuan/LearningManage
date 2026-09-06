package com.spt.learningmanage.model.dto.agent;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AgentReportQueryRequest {
    private String reportType;
    private Long projectId;
    private Long teamId;
    @Min(1)
    private long current = 1;
    @Min(1) @Max(50)
    private long pageSize = 10;
}
