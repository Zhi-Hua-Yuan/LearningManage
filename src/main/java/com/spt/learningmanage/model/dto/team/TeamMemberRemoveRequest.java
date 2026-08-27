package com.spt.learningmanage.model.dto.team;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 移除团队成员请求。
 */
@Data
public class TeamMemberRemoveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "目标用户ID不能为空")
    private Long targetUserId;
}
