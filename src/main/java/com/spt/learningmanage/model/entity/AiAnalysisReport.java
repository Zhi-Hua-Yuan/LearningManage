package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_analysis_report")
public class AiAnalysisReport {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String reportId;
    private String reportType;
    private Integer schemaVersion;
    private Long projectId;
    private Long teamId;
    private Long creatorUserId;
    private String sourceRunId;
    private Long sourceDataVersion;
    private String managerSummary;
    private String publicSummary;
    private String memberMetricsJson;
    private String recommendationsJson;
    private LocalDateTime contentPurgedAt;
    private String contentHash;
    private String model;
    private String promptCode;
    private Integer promptVersion;
    private String traceId;
    private LocalDateTime generatedAt;
    private LocalDateTime deletedAt;
    @TableLogic
    private Integer isDelete;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
