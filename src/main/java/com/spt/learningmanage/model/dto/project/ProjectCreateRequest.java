package com.spt.learningmanage.model.dto.project;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ProjectCreateRequest {
    private String name;

    private String goal;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") // 告诉后端：如果看到带时间的格式，自动帮我转成 LocalDate
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDate endDate;
}
