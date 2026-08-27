package com.spt.learningmanage.model.vo.review;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 团队共享摘要视图。类型层面不包含私人正文和私人任务列表。
 */
@Data
public class WeeklyReviewSharedVO {
    private Long id;
    private Long authorUserId;
    private Integer year;
    private Integer weekNo;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long focusProjectId;
    private String focusProjectName;
    private String sharedSummary;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
