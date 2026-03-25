package com.spt.learningmanage.model.dto.milestone;

import lombok.Data;

@Data
public class MilestoneQueryRequest {
    private Long projectId;
    private String keyword;
}

