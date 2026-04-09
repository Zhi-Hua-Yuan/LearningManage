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
     * 根据ID获取周总结详情
     * @param id 周总结ID
     * @return 周总结详细信息
     */
    WeeklyReview getReviewById(Long id);

    /**
     * 更新周总结
     */
    void updateReview(WeeklyReview weeklyReview);

    /**
     * 删除周总结
     */
    void deleteReview(Long id);

    /**
     * 获取历史周总结列表。
     */
    List<WeeklyReview> listHistory();
}

