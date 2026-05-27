package com.spt.learningmanage.model.dto.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 修改团队成员角色请求
 */
@Data
public class TeamMemberRoleUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 团队ID
     */
    @NotNull(message = "团队ID不能为空")
    private Long teamId;

    /**
     * 被修改角色的用户ID
     */
    @NotNull(message = "目标用户ID不能为空")
    private Long targetUserId;

    /**
     * 目标角色：ADMIN / MEMBER
     */
    @NotBlank(message = "目标角色不能为空")
    private String role;
}
