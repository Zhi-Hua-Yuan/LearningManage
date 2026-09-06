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
    LIST_REPLAN("list-replan", "清单智能重排"),
    RAG_PROJECT_ASK("rag-project-ask", "项目知识问答"),
    AGENT_PROJECT_RISK("project-risk-report", "项目风险分析"),
    AGENT_TEAM_WORKLOAD("team-workload-report", "团队负载分析");

    private final String code;

    private final String description;

    AiSceneEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
