package com.spt.learningmanage.model.dto.review;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 周复盘保存请求。未提供 visibilityScope 时按 PRIVATE 处理。
 */
@Data
public class WeeklyReviewSaveRequest {
    private Long id;
    private Integer year;
    private Integer weekNo;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer completedTaskCount;
    private String focusProjectName;
    private Long focusProjectId;
    private String visibilityScope;
    private Long teamId;
    private String sharedSummary;
    private String reflection;
    private String nextPlan;
    private List<Long> taskIds;
}
