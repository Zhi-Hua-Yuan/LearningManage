package com.spt.learningmanage.model.dto.milestone;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MilestoneUpdateRequest {
    private Long id;
    private String name;
    private Integer orderNo;
    private BigDecimal progress;
}

