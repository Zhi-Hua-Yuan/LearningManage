package com.spt.learningmanage.model.vo.task;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskAssignmentResultVO {
    private Long taskId;
    private Long fromAssigneeUserId;
    private Long toAssigneeUserId;
    private Long assignedByUserId;
    private LocalDateTime assignedAt;
    private String action;
    private Long logId;
    private Boolean changed;
}
