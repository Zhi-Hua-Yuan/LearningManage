package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 任务负责人变更审计记录。 */
@Data
@TableName("task_assignment_log")
public class TaskAssignmentLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long taskId;
    private Long fromAssigneeUserId;
    private Long toAssigneeUserId;
    private Long assignedByUserId;
    private String action;
    private String reason;
    private LocalDateTime createTime;
}
