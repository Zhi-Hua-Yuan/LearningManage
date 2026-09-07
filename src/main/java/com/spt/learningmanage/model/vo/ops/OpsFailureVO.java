package com.spt.learningmanage.model.vo.ops;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OpsFailureVO {
    private String source;
    private String status;
    private String failureType;
    private String traceId;
    private LocalDateTime occurredAt;
}
