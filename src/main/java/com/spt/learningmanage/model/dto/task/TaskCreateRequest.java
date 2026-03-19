package com.spt.learningmanage.model.dto.task;

import lombok.Data;

import java.time.LocalDate;

@Data
public class TaskCreateRequest {
    private String title;
    private String description;
    private Long projectId;
    private Integer priority;
    private LocalDate dueDate;
}
