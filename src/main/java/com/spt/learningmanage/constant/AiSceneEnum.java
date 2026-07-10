package com.spt.learningmanage.constant;

import lombok.Getter;

/**
 * AI 业务场景。
 *
 * 场景编码会写入草稿和调用日志，发布后不应随意修改。
 */
@Getter
public enum AiSceneEnum {

    TASK_BREAKDOWN("task-breakdown", "任务拆解"),
    WEEKLY_POLISH("weekly-polish", "周总结润色"),
    TODAY_ORDER("today-order", "今日任务排序"),
    DAILY_REVIEW_RENAME("daily-review-rename", "日报任务改名"),
    LIST_REPLAN("list-replan", "清单智能重排");

    private final String code;

    private final String description;

    AiSceneEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
