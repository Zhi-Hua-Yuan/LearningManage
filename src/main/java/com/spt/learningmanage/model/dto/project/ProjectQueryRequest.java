package com.spt.learningmanage.model.dto.project;

import lombok.Data;

@Data
public class ProjectQueryRequest {
    private Integer status;
    private String keyword;
    private Long pageNum = 1L;
    private Long pageSize = 10L;
}
