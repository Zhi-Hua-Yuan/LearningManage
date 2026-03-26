package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("weekly_review")
public class WeeklyReview {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private Integer year;
    private Integer weekNo;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer completedTaskCount;
    private String focusProjectName;
    private String reflection;
    private String nextPlan;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

