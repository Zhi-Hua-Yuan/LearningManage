package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_admin_operation_log")
public class AiAdminOperationLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long operatorUserId;
    private String operationType;
    private String targetType;
    private String targetId;
    private String argumentSummary;
    private String resultSummary;
    private String status;
    private String traceId;
    private LocalDateTime createTime;
}
