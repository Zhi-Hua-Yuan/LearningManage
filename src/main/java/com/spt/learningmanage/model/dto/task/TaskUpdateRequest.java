package com.spt.learningmanage.model.dto.task;

import lombok.Data;

import java.time.LocalDate;

@Data
public class TaskUpdateRequest {
    private Long id;
    private String title;
    private String description;
    private Integer status;
    private Integer priority;
    private LocalDate dueDate;
}
