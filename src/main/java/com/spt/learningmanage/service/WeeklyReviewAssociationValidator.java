package com.spt.learningmanage.service;

import com.spt.learningmanage.constant.WeeklyReviewVisibilityScopeEnum;
import com.spt.learningmanage.model.review.WeeklyReviewAssociationContext;

import java.util.Collection;

/** Validates and normalizes resources attached to a weekly review. */
public interface WeeklyReviewAssociationValidator {

    WeeklyReviewAssociationContext validate(
            Long actorUserId,
            WeeklyReviewVisibilityScopeEnum scope,
            Long teamId,
            Long focusProjectId,
            Collection<Long> taskIds
    );
}
