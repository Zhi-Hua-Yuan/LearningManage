package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("task_title_rename_log")
public class TaskTitleRenameLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String operationId;
    private Long userId;
    private Long taskId;
    private LocalDate reviewDate;
    private String oldTitle;
    private String newTitle;
    private String reason;
    private Integer confidence;
    private Integer isApplied;
    private LocalDateTime appliedAt;
    private Integer isRollback;
    private LocalDateTime rollbackAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

