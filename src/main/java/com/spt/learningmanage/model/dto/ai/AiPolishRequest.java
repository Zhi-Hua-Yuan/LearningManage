package com.spt.learningmanage.model.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "AI 周总结润色请求")
public class AiPolishRequest {

    @Schema(description = "本周已完成任务ID列表（可选）", example = "[1001,1002,1003]")
    private List<Long> taskIds;

    @Schema(description = "本周反思（可选）", example = "执行力有进步，但时间分配仍需优化")
    private String reflection;
}
