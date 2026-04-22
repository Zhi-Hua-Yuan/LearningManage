package com.spt.learningmanage.model.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "清单任务重排取消请求")
public class AiListReplanCancelRequest {

    @Schema(description = "重排操作ID", example = "20260422_replan_9ab27d5f")
    private String operationId;
}
