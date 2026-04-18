package com.spt.learningmanage.model.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "任务批量改名回滚请求")
public class TaskBatchRollbackRequest {

    @Schema(description = "建议批次ID", example = "20260418_rename_9ab27d5f")
    private String operationId;
}

