package com.spt.learningmanage.model.dto.task;

import lombok.Data;

import java.time.LocalDate;

@Data
public class TaskCreateRequest {
    private String title;
    private String description;
    private Long projectId;
    private Long milestoneId;
    private Integer priority = 0;
    private LocalDate dueDate;
}
