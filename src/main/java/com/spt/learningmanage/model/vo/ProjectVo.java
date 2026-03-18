package com.spt.learningmanage.model.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProjectVo {
    private Long id;
    private Long userId;
    private String name;
    private String goal;
    private Integer status;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
