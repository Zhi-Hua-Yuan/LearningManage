package com.spt.learningmanage.model.dto.ai;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiCallLogQueryRequest {

    private String scene;

    private Integer status;

    private String modelName;

    private String promptType;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long pageNum = 1L;

    private Long pageSize = 10L;
}
