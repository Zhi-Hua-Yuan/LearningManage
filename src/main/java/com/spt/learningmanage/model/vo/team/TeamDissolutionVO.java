package com.spt.learningmanage.model.vo.team;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TeamDissolutionVO {
    private Long teamId;
    private LocalDateTime dissolvedAt;
    private int removedMemberCount;
}
