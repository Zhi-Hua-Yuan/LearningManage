package com.spt.learningmanage.model.dto.team;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class TeamOwnerTransferRequest {
    @NotNull(message = "团队ID不能为空")
    @Positive(message = "团队ID必须为正数")
    private Long teamId;

    @NotNull(message = "目标用户ID不能为空")
    @Positive(message = "目标用户ID必须为正数")
    private Long targetUserId;
}
