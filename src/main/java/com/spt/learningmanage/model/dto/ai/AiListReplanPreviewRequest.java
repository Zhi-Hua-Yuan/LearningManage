package com.spt.learningmanage.model.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "清单任务重排预览请求")
public class AiListReplanPreviewRequest {

    @Schema(description = "清单ID（project.id）", example = "1001")
    private Long listId;
}
