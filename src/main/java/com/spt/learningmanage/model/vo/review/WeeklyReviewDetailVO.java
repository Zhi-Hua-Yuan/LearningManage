package com.spt.learningmanage.model.vo.review;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class WeeklyReviewDetailVO {

    private Long id;
    private Long authorUserId;
    private Integer year;
    private Integer weekNo;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer completedTaskCount;
    private String visibilityScope;
    private Long teamId;
    private Long focusProjectId;
    private String focusProjectName;
    private String sharedSummary;
    private String reflection;
    private String nextPlan;
    private List<Long> taskIds;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
