package com.spt.learningmanage.model.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "单条任务改名建议")
public class TitleRenameSuggestionItemVO {

    @Schema(description = "任务ID", example = "101")
    private Long taskId;

    @Schema(description = "原任务名称", example = "背单词")
    private String oldTitle;

    @Schema(description = "建议新任务名称", example = "完成核心词汇第11-12单元记忆")
    private String newTitle;

    @Schema(description = "建议原因", example = "标题更具体，可直接执行和验收")
    private String reason;

    @Schema(description = "建议置信度（0-100）", example = "86")
    private Integer confidence;
}

