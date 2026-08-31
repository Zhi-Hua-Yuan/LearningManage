package com.spt.learningmanage.model.query.review;

import lombok.Data;

/**
 * Weekly review statistics projection for the project with the most completed
 * tasks assigned to an actor.
 */
@Data
public class WeeklyReviewFocusProjectRow {

    private Long projectId;
    private String projectName;
    private Integer completedCount;
}
