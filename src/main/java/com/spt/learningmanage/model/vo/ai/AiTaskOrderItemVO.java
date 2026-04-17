package com.spt.learningmanage.model.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI 推荐排序中的单个任务")
public class AiTaskOrderItemVO {

    @Schema(description = "任务ID", example = "102")
    private Long taskId;

    @Schema(description = "任务标题", example = "完成核心词汇第1-10单元")
    private String title;

    @Schema(description = "推荐顺位（1开始）", example = "1")
    private Integer rank;

    @Schema(description = "综合分（0-100）", example = "88")
    private Integer score;

    @Schema(description = "难度（1-5）", example = "3")
    private Integer difficulty;

    @Schema(description = "成本（1-5）", example = "2")
    private Integer cost;

    @Schema(description = "效益（1-5）", example = "5")
    private Integer benefit;

    @Schema(description = "预估耗时（分钟）", example = "30")
    private Integer estimatedMinutes;

    @Schema(description = "推荐原因", example = "效益高且可在30分钟内完成，建议优先处理")
    private String reason;
}

