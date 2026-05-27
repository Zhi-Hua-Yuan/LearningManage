package com.spt.learningmanage.model.dto.project;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

@Data
public class TeamProjectCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 团队ID
     */
    private Long teamId;

    private String name;

    private String goal;

    private String icon;

    private String color;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
}
