package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_call_log")
public class AiCallLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String scene;

    private String modelName;

    private String promptType;

    private String requestText;

    private String responseText;

    private Integer status;

    private String errorMessage;

    private Long costTimeMs;

    private Integer retryCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
