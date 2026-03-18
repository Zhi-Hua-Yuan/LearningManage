package com.spt.learningmanage.model.vo.task;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TaskVo {
    private Long id;
    private Long projectId;
    private Long userId;
    private String title;
    private String description;
    private Integer status;
    private Integer priority;
    private LocalDate dueDate;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
