package com.spt.learningmanage.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MilestoneVo {
    private Long id;
    private Long projectId;
    private Long userId;
    private String name;
    private Integer orderNo;
    private BigDecimal progress;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

