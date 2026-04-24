package com.spt.learningmanage.model.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "清单任务重排预览明细")
public class AiListReplanPreviewItemVO {

    @Schema(description = "任务ID（前端内部字段，不建议展示）", example = "101")
    private Long taskId;

    @Schema(description = "原任务标题", example = "背单词")
    private String oldTitle;

    @Schema(description = "建议任务标题", example = "完成核心词汇第1-12单元记忆")
    private String newTitle;

    @Schema(description = "原优先级", example = "1")
    private Integer oldPriority;

    @Schema(description = "建议优先级", example = "3")
    private Integer newPriority;

    @Schema(description = "原截止日期", example = "2026-04-22")
    private LocalDate oldDueDate;

    @Schema(description = "建议截止日期", example = "2026-04-20")
    private LocalDate newDueDate;

    @Schema(description = "截止日期是否发生变化", example = "true")
    private Boolean dueChanged;

    @Schema(description = "截止日期变化天数（new-old，负数为提前，正数为顺延）", example = "-2")
    private Integer dueDeltaDays;

    @Schema(description = "截止日期变化标签", example = "提前2天")
    private String dueChangeLabel;

    @Schema(description = "建议置信度（0-100）", example = "86")
    private Integer confidence;

    @Schema(description = "建议原因", example = "根据执行节奏和收益优先级调整")
    private String reason;
}
