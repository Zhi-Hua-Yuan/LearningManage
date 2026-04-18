package com.spt.learningmanage.model.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "单条任务改名提交项")
public class TaskRenameItemDTO {

    @Schema(description = "任务ID", example = "101")
    private Long taskId;

    @Schema(description = "改名前标题", example = "背单词")
    private String oldTitle;

    @Schema(description = "改名后标题", example = "完成核心词汇第11-12单元记忆")
    private String newTitle;
}

