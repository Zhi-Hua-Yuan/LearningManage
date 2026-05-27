package com.spt.learningmanage.model.dto.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 创建团队请求
 */
@Data
public class TeamCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 团队名称
     */
    @NotBlank(message = "团队名称不能为空")
    @Size(max = 60, message = "团队名称不能超过60个字符")
    private String name;

    /**
     * 团队描述
     */
    @Size(max = 200, message = "团队描述不能超过200个字符")
    private String description;
}
