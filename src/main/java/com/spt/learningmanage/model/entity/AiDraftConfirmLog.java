package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_draft_confirm_log")
public class AiDraftConfirmLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String draftId;
    private String operationId;
    private String scene;
    private Long businessId;
    private String traceId;
    private LocalDateTime createTime;
}
