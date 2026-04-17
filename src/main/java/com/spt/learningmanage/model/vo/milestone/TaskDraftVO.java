package com.spt.learningmanage.model.vo.milestone;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI 生成的任务草稿")
public class TaskDraftVO {
    @Schema(description = "任务名称", example = "完成每日听力训练30分钟")
    private String name;

    @Schema(description = "任务优先级：0=无/稍后，1=低，2=中，3=高", example = "2")
    private Integer priority;

    @Schema(description = "任务截止日期（yyyy-MM-dd）", example = "2026-04-30")
    private String dueDate;
}

