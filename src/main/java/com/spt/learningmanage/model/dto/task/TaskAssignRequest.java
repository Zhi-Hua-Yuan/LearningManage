package com.spt.learningmanage.model.dto.task;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

/** 任务负责人变更请求。expectedAssigneeUserId 必须显式提供（可为 null）。 */
@Getter
@Setter
public class TaskAssignRequest {
    private Long taskId;
    private Long assigneeUserId;
    private Long expectedAssigneeUserId;
    private String reason;

    private boolean expectedAssigneeUserIdPresent;

    @JsonSetter("expectedAssigneeUserId")
    public void setExpectedAssigneeUserId(Long expectedAssigneeUserId) {
        this.expectedAssigneeUserId = expectedAssigneeUserId;
        this.expectedAssigneeUserIdPresent = true;
    }

    @JsonIgnore
    public boolean isExpectedAssigneeUserIdPresent() {
        return expectedAssigneeUserIdPresent;
    }
}
