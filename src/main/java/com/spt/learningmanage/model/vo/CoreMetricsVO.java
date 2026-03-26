package com.spt.learningmanage.model.vo;

import lombok.Data;

@Data
public class CoreMetricsVO {
    /**
     * 进行中的项目数
     */
    private Integer ongoingProjectCount;

    /**
     * 逾期且未完成的任务数
     */
    private Integer overdueTaskCount;

    /**
     * 今日到期且未完成的任务数
     */
    private Integer dueTodayTaskCount;
}

