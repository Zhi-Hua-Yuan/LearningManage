package com.spt.learningmanage.model.dto.review;

import lombok.Data;

import java.util.List;

@Data
public class WeeklyReviewSaveRequest {

    private Integer year;
    private Integer weekNo;
    private String visibilityScope;
    private Long teamId;
    private Long focusProjectId;
    private String reflection;
    private String nextPlan;
    private String sharedSummary;
    private List<Long> taskIds;
}
