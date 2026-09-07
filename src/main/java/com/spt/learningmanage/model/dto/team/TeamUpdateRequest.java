package com.spt.learningmanage.model.dto.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TeamUpdateRequest {
    @NotNull(message = "团队ID不能为空")
    @Positive(message = "团队ID必须为正数")
    private Long teamId;

    @NotBlank(message = "团队名称不能为空")
    @Size(max = 60, message = "团队名称不能超过60个字符")
    private String name;

    @Size(max = 200, message = "团队描述不能超过200个字符")
    private String description;
}
