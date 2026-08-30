package com.spt.learningmanage.model.vo.review;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Team-facing projection. Deliberately has no reflection, nextPlan or taskIds.
 */
@Data
public class WeeklyReviewSharedVO {

    private Long id;
    private WeeklyReviewAuthorSummaryVO author;
    private Integer year;
    private Integer weekNo;
    private LocalDate startDate;
    private LocalDate endDate;
    private WeeklyReviewFocusProjectSummaryVO focusProject;
    private String sharedSummary;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
