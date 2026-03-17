package com.spt.learningmanage.model.dto.project;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ProjectUpdateRequest {
    private Long id;

    private String name;

    private String goal;

    private Integer status;
    private LocalDate startDate;
    private LocalDate endDate;
}
