package com.spt.learningmanage.model.vo.task;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskStatusChangeVO {
    private Boolean changed;
    private Integer finalStatus;
    private LocalDateTime completedAt;
    private Boolean idempotentReplay;
}
