package com.spt.learningmanage.model.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "任务批量改名结果")
public class TaskBatchRenameVO {

    @Schema(description = "建议批次ID", example = "20260418_rename_9ab27d5f")
    private String operationId;

    @Schema(description = "成功改名数量", example = "5")
    private Integer successCount;

    @Schema(description = "跳过数量", example = "1")
    private Integer skipCount;

    @Schema(description = "成功更新的任务ID列表", example = "[101,102,103]")
    private List<Long> updatedTaskIds;
}

