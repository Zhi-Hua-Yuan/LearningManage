package com.spt.learningmanage.model.dto.task;

import lombok.Data;

/**
 * Assignment command. A null assignee means unassign.
 */
@Data
public class TaskAssignRequest {
    private Long taskId;
    private Long assigneeUserId;
    private Long expectedAssigneeUserId;
    /**
     * Set to true when the caller intentionally expects the current assignee
     * to be null. A non-null expectedAssigneeUserId is always treated as set.
     */
    private Boolean expectedAssigneeProvided;
    private String reason;
}
