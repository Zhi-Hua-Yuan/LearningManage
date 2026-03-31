package com.spt.learningmanage.model.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI 任务拆解请求")
public class AiBreakdownRequest {

    @Schema(description = "目标", example = "三个月内通过英语六级")
    private String target;

    @Schema(description = "目标描述", example = "目前词汇和听力较弱，希望系统提升")
    private String description;

    @Schema(description = "周期", example = "12周")
    private String duration;
}

