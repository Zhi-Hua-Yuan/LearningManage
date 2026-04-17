package com.spt.learningmanage.model.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "AI 今日任务推荐排序请求")
public class AiTodayOrderRequest {

    @Schema(description = "任务ID列表（可选，空则后端自动查询今天到期且未完成任务）", example = "[101,102,103]")
    private List<Long> taskIds;

    @Schema(description = "时区", example = "Asia/Shanghai")
    private String timezone = "Asia/Shanghai";

    @Schema(description = "当前时间（可选，ISO-8601）", example = "2026-04-17T10:30:00")
    private String now;

    @Schema(description = "偏好策略：balanced/benefit_first/quick_win", example = "balanced")
    private String strategy = "balanced";

    @Schema(description = "最多返回任务数（1-50）", example = "20")
    private Integer limit = 20;
}

