package com.spt.learningmanage.model.vo.team;

import lombok.Data;

import java.time.LocalDateTime;

/** 团队成员关系终止结果。 */
@Data
public class TeamMembershipTerminationVO {

    private Long teamId;
    private Long memberUserId;
    private String action;
    private Integer unassignedTaskCount;
    private LocalDateTime terminatedAt;
}
