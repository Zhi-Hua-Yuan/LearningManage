package com.spt.learningmanage.model.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI 周总结润色请求")
public class AiPolishRequest {

    @Schema(description = "本周完成任务数", example = "8")
    private Integer taskCount;

    @Schema(description = "核心项目名称", example = "考研复习计划")
    private String focusProject;

    @Schema(description = "本周反思", example = "执行力有进步，但时间分配仍需优化")
    private String reflection;
}

