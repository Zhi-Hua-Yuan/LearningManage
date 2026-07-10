package com.spt.learningmanage.model.vo.ai;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Prompt 模板版本列表项。
 */
@Data
public class PromptTemplateVO {

    private Long id;

    private String templateCode;

    private String scene;

    private String templateName;

    private Integer version;

    private Integer enabled;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
