package com.spt.learningmanage.model.dto.task;

import lombok.Data;

import java.time.LocalDate;

@Data
public class TaskCreateRequest {
    private String title;
    private String description;
    private Long projectId;
    private Long milestoneId;
    /** 可选的初始负责人；个人项目仅允许项目所有者，团队项目可为空。 */
    private Long assigneeUserId;
    private Integer priority = 0;
    private LocalDate dueDate;
}
