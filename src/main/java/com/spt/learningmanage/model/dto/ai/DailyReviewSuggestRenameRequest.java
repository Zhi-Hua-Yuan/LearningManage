package com.spt.learningmanage.model.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "日报回顾改名建议请求")
public class DailyReviewSuggestRenameRequest {

    @Schema(description = "回顾日期（yyyy-MM-dd），为空默认今天", example = "2026-04-18")
    private String reviewDate;

    @Schema(description = "偏好策略：balanced/clarity_first", example = "balanced")
    private String strategy = "balanced";

    @Schema(description = "最大改名数（1-50），为空默认全部未完成任务", example = "10")
    private Integer maxEdits;

    @Schema(description = "任务ID列表（可选，空则使用当天到期任务）", example = "[101,102,103]")
    private List<Long> taskIds;
}

