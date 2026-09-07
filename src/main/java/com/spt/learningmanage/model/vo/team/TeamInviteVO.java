package com.spt.learningmanage.model.vo.team;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TeamInviteVO {
    private Long teamId;
    private String inviteCode;
}
