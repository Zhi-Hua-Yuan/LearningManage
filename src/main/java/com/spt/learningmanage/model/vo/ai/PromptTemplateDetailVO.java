package com.spt.learningmanage.model.vo.ai;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Prompt 模板版本详情。
 *
 * 与列表 VO 分别完整定义字段，不使用继承。
 */
@Data
public class PromptTemplateDetailVO {

    private Long id;

    private String templateCode;

    private String scene;

    private String templateName;

    private String templateContent;

    private Integer version;

    private Integer enabled;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
