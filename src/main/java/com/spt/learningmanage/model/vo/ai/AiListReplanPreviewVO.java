package com.spt.learningmanage.model.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "清单任务重排预览结果")
public class AiListReplanPreviewVO {

    @Schema(description = "重排操作ID", example = "20260422_replan_9ab27d5f")
    private String operationId;

    @Schema(description = "发生变化的任务数量", example = "5")
    private Integer changedCount;

    @Schema(description = "重排预览任务列表")
    private List<AiListReplanPreviewItemVO> previewTasks;
}
