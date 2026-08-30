package com.spt.learningmanage.service;

import com.spt.learningmanage.model.entity.WeeklyReview;
import com.spt.learningmanage.model.review.WeeklyReviewReadableAssociations;

import java.util.List;

/** Resolves persisted review associations against the actor's current access. */
public interface WeeklyReviewReadAssociationResolver {

    WeeklyReviewReadableAssociations resolve(
            Long actorUserId,
            List<WeeklyReview> reviews
    );
}
