package com.spt.learningmanage.constant;

import lombok.Getter;

/**
 * AI Prompt 模板编码。
 *
 * 编码用于调用记录和后续数据库模板查询，内容迭代时保持编码稳定。
 */
@Getter
public enum AiPromptCodeEnum {

    TASK_BREAKDOWN_DEFAULT("task-breakdown.default", AiSceneEnum.TASK_BREAKDOWN, "任务拆解-普通模式"),
    TASK_BREAKDOWN_DETAILED("task-breakdown.detailed", AiSceneEnum.TASK_BREAKDOWN, "任务拆解-详细模式"),
    WEEKLY_POLISH_DEFAULT("weekly-polish.default", AiSceneEnum.WEEKLY_POLISH, "周总结润色"),
    TODAY_ORDER_DEFAULT("today-order.default", AiSceneEnum.TODAY_ORDER, "今日任务排序"),
    DAILY_REVIEW_RENAME_DEFAULT("daily-review-rename.default", AiSceneEnum.DAILY_REVIEW_RENAME, "日报任务改名"),
    LIST_REPLAN_PREVIEW("list-replan.preview", AiSceneEnum.LIST_REPLAN, "清单智能重排预览");

    private final String code;

    private final AiSceneEnum scene;

    private final String description;

    AiPromptCodeEnum(String code, AiSceneEnum scene, String description) {
        this.code = code;
        this.scene = scene;
        this.description = description;
    }
}
