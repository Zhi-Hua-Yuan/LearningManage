package com.spt.learningmanage.model.query.agent;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AgentProjectTaskRow {
    private Long id;
    private String title;
    private Integer status;
    private Integer priority;
    private LocalDate dueDate;
    private Long assigneeUserId;
    private LocalDateTime completedAt;
    private LocalDateTime updateTime;
}

