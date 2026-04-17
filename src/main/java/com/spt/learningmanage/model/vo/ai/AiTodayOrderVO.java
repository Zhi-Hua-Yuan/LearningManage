package com.spt.learningmanage.model.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "AI 今日任务推荐排序结果")
public class AiTodayOrderVO {

    @Schema(description = "采用策略", example = "balanced")
    private String strategy;

    @Schema(description = "生成时间", example = "2026-04-17T10:30:02")
    private String generatedAt;

    @Schema(description = "是否使用规则兜底", example = "false")
    private Boolean fallbackUsed;

    @Schema(description = "推荐任务列表")
    private List<AiTaskOrderItemVO> items;
}

