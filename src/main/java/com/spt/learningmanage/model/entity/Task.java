package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("task")
public class Task {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long projectId;
    private Long milestoneId;
    /** Task creator; the physical column remains task.user_id for compatibility. */
    @TableField("user_id")
    private Long createdByUserId;
    /** Current assignee; null means unassigned. */
    private Long assigneeUserId;
    /** User who performed the latest effective assignment. */
    private Long assignedByUserId;
    /** Time of the latest effective assignment. */
    private LocalDateTime assignedAt;
    private String title;
    private String description;
    private Integer status;
    private Integer priority;
    private LocalDate dueDate;
    private LocalDateTime completedAt;
    private LocalDateTime deletedAt;
    private Integer deleteSource;
    @TableLogic
    private Integer isDelete;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
