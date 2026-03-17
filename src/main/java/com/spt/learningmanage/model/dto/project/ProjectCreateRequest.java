package com.spt.learningmanage.model.dto.project;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ProjectCreateRequest {
    private String name;

    private String goal;

    private LocalDate startDate;
    private LocalDate endDate;
}
