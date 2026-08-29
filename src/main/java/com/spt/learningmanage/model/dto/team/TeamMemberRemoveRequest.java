package com.spt.learningmanage.model.dto.team;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** 请求管理员移除团队成员。 */
@Data
public class TeamMemberRemoveRequest {

    @NotNull(message = "teamId 不能为空")
    @Positive(message = "teamId 必须为正数")
    private Long teamId;

    @NotNull(message = "targetUserId 不能为空")
    @Positive(message = "targetUserId 必须为正数")
    private Long targetUserId;
}
