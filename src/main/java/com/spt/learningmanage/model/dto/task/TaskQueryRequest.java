package com.spt.learningmanage.model.dto.task;

import lombok.Data;

@Data
public class TaskQueryRequest {
    private String title;
    private Integer status;
    private Long projectId;
    private Long pageNum = 1L;
    private Long pageSize = 10L;
    private Boolean isOverdue;
}
