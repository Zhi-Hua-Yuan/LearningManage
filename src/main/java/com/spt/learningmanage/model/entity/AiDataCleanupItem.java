package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_data_cleanup_item")
public class AiDataCleanupItem {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String runId;
    private String resourceType;
    private LocalDateTime cutoffTime;
    private String status;
    private Long cursorId;
    private Long scannedCount;
    private Long estimatedCount;
    private Long redactedCount;
    private Long deletedCount;
    private String errorSummary;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
