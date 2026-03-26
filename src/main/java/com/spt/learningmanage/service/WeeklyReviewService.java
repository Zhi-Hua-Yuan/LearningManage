package com.spt.learningmanage.service;

import com.spt.learningmanage.model.entity.WeeklyReview;

import java.util.List;

public interface WeeklyReviewService {

    /**
     * 获取或生成当前周的周总结草稿。
     */
    WeeklyReview getCurrentWeekReview();

    /**
     * 保存或更新周总结。
     */
    void saveReview(WeeklyReview weeklyReview);

    /**
     * 获取历史周总结列表。
     */
    List<WeeklyReview> listHistory();
}

