package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_analysis_report_source")
public class AiAnalysisReportSource {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String reportId;
    private String citationId;
    private String sourceType;
    private Long sourceId;
    private String documentKey;
    private Integer chunkIndex;
    private String contentHash;
    private String payloadHash;
    private String titleSnapshot;
    private LocalDateTime createTime;
}
