package com.spt.learningmanage.model.dto.ai;

import lombok.Data;

@Data
public class PromptTemplateQueryRequest {

    private String templateCode;

    private String scene;

    private Integer enabled;

    private Long pageNum = 1L;

    private Long pageSize = 10L;
}
