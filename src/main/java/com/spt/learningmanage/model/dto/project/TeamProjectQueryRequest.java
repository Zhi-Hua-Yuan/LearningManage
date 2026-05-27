package com.spt.learningmanage.model.dto.project;

import lombok.Data;

import java.io.Serializable;

@Data
public class TeamProjectQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 团队ID
     */
    private Long teamId;

    private Integer status;

    /**
     * 关键字搜索，仅按项目名称模糊查询
     */
    private String keyword;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
