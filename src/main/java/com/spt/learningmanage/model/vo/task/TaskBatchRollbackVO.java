package com.spt.learningmanage.model.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "任务批量改名回滚结果")
public class TaskBatchRollbackVO {

    @Schema(description = "建议批次ID", example = "20260418_rename_9ab27d5f")
    private String operationId;

    @Schema(description = "成功回滚数量", example = "5")
    private Integer rollbackCount;
}

