package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("ai_replan_item")
public class AiReplanItem {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String operationId;
    private Long taskId;
    private String oldTitle;
    private String newTitle;
    private Integer oldPriority;
    private Integer newPriority;
    private LocalDate oldDueDate;
    private LocalDate newDueDate;
    private Integer confidence;
    private String reason;
    private LocalDateTime taskSnapshotUpdateTime;
}
