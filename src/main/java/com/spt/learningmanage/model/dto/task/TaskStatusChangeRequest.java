package com.spt.learningmanage.model.dto.task;

import lombok.Data;

@Data
public class TaskStatusChangeRequest {
    private Long taskId;
    private Integer targetStatus;
    /**
     * 客户端生成的幂等键（建议 UUID）。
     */
    private String clientRequestId;
    /**
     * 可选：期望的当前状态，用于前端并发保护。
     */
    private Integer expectedStatus;
}
