package com.spt.learningmanage.model.dto.milestone;

import lombok.Data;

@Data
public class MilestoneCreateRequest {
    private Long projectId;
    private String name;
}

