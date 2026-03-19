package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
    private Long userId;
    private String title;
    private String description;
    private Integer status;
    private Integer priority;
    private LocalDate dueDate;
    private LocalDateTime completedAt;
    @TableLogic
    private Integer isDelete;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
