package com.spt.learningmanage.model.vo.review;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 作者完整周复盘视图。仅作者可获得该视图。
 */
@Data
public class WeeklyReviewVO {
    private Long id;
    private Long userId;
    private String visibilityScope;
    private Long teamId;
    private Long focusProjectId;
    private String focusProjectName;
    private Integer year;
    private Integer weekNo;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer completedTaskCount;
    private String sharedSummary;
    private String reflection;
    private String nextPlan;
    private List<Long> taskIds;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
