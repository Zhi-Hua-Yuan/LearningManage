package com.spt.learningmanage.model.dto.ai;

import lombok.Data;

/**
 * 创建 Prompt 模板新版本请求。
 *
 * scene、version 和 enabled 均由服务端生成，不接受调用方传入。
 */
@Data
public class PromptTemplateCreateVersionRequest {

    private String templateCode;

    private String templateName;

    private String templateContent;

    private String remark;
}
