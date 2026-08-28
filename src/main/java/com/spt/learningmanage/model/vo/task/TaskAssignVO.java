package com.spt.learningmanage.model.vo.task;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskAssignVO {
    private Long taskId;
    private Boolean changed;
    private Long previousAssigneeUserId;
    private Long assigneeUserId;
    private Long assignedByUserId;
    private LocalDateTime assignedAt;
}
