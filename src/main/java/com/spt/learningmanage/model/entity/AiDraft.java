package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_draft")
public class AiDraft {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String draftId;
    private Long userId;
    private String scene;
    private String payloadJson;
    private String inputHash;
    private Integer status;
    private LocalDateTime expireAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime canceledAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
