package com.spt.learningmanage.model.dto.project;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ProjectCreateRequest {
    private String name;

    private String goal;

    @Schema(description = "清单图标")
    private String icon;

    @Schema(description = "清单颜色，#RRGGBB")
    private String color;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
}
