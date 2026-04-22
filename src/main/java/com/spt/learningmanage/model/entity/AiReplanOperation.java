package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_replan_operation")
public class AiReplanOperation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String operationId;
    private Long userId;
    private Long projectId;
    /**
     * 0-PREVIEW 1-CONFIRMED 2-CANCELED 3-EXPIRED
     */
    private Integer status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime canceledAt;
}
