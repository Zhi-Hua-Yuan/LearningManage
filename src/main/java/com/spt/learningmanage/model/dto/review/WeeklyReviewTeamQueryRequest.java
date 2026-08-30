package com.spt.learningmanage.model.dto.review;

import lombok.Data;

@Data
public class WeeklyReviewTeamQueryRequest {

    private Long teamId;
    private Long current = 1L;
    private Long size = 20L;
}
