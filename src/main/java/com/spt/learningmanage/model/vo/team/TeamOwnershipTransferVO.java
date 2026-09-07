package com.spt.learningmanage.model.vo.team;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TeamOwnershipTransferVO {
    private Long teamId;
    private Long previousOwnerUserId;
    private Long newOwnerUserId;
    private LocalDateTime transferredAt;
}
