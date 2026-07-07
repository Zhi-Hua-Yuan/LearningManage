package com.spt.learningmanage.model.dto.ai;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiCallLogStatsRequest {

    private String scene;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
