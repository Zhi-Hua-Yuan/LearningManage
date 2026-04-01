package com.spt.learningmanage.model.vo.dashboard;

import lombok.Data;

@Data
public class DailyTrendVO {
    /**
     * 日期，格式 yyyy-MM-dd
     */
    private String date;

    private Integer completedCount;
}

