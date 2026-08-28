package com.spt.learningmanage.model.query.task;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 负责人历史查询行模型。
 *
 * <p>该类型只承载 assignment log 及用户展示名的扁平查询结果，不暴露
 * User/Task 实体，避免把账户、角色或任务私有字段带入 API 映射。</p>
 */
@Data
public class TaskAssignmentHistoryRow {

    private Long id;

    private Long taskId;

    private String action;

    private Long fromAssigneeUserId;

    private String fromAssigneeUsername;

    private Long toAssigneeUserId;

    private String toAssigneeUsername;

    private Long assignedByUserId;

    private String assignedByUsername;

    private String reason;

    private LocalDateTime createTime;
}
