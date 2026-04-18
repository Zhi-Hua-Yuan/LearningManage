package com.spt.learningmanage.model.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "任务批量改名请求")
public class TaskBatchRenameRequest {

    @Schema(description = "建议批次ID", example = "20260418_rename_9ab27d5f")
    private String operationId;

    @Schema(description = "确认改名条目")
    private List<TaskRenameItemDTO> items;
}

