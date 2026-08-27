package com.spt.learningmanage.model.vo.task;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskAssignmentLogVO {
    private Long id;
    private Long taskId;
    private Long fromAssigneeUserId;
    private Long toAssigneeUserId;
    private Long assignedByUserId;
    private String action;
    private String reason;
    private LocalDateTime createTime;
}
