package com.spt.learningmanage.model.vo.team;

import lombok.Data;

@Data
public class TeamDissolutionCheckVO {
    private Long teamId;
    private boolean canDissolve;
    private long activeProjectCount;
    private long sharedReviewCount;
}
